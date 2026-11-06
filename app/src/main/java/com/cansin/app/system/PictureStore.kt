package com.cansin.app.system

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.ImageDecoder
import android.os.Build
import android.net.Uri
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.cansin.app.data.Side
import com.cansin.app.domain.CropSquare
import com.cansin.app.domain.PictureChange
import com.cansin.app.domain.PictureRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * P38: pictures behind +1 buttons, as small JPEGs in app-private storage (files/pictures).
 * Never exported, shared or backed up (allowBackup=false); "Delete all data" removes them.
 */
class PictureStore(context: Context) {
    private val dir = File(context.filesDir, "pictures")
    private val cache = LruCache<String, Bitmap>(CACHE_ENTRIES)
    private val _version = MutableStateFlow(0)

    /** Bumps on every change so screens reload. */
    val version: StateFlow<Int> = _version

    private fun file(key: String): File? = key.takeIf(PictureRules::isValid)?.let { File(dir, "$it.jpg") }

    suspend fun exists(key: String): Boolean = withContext(Dispatchers.IO) { file(key)?.isFile == true }

    suspend fun load(key: String, maxPx: Int = PictureRules.OUTPUT_PX): Bitmap? = withContext(Dispatchers.IO) { loadNow(key, maxPx) }

    /** Blocking load for the widget (already on a background thread). */
    fun loadNow(key: String, maxPx: Int): Bitmap? {
        val f = file(key)?.takeIf { it.isFile } ?: return null
        val cacheKey = "$key@$maxPx@${f.lastModified()}"
        cache.get(cacheKey)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = PictureRules.sampleSize(bounds.outWidth, bounds.outHeight, maxPx) }
        val bitmap = BitmapFactory.decodeFile(f.path, options) ?: return null
        val sized = if (bitmap.width > maxPx) bitmap.scale(maxPx, maxPx) else bitmap
        cache.put(cacheKey, sized)
        return sized
    }

    /** Round copy (transparent corners) for widget circles. */
    fun loadRoundNow(key: String, px: Int): Bitmap? {
        val square = loadNow(key, px) ?: return null
        val out = createBitmap(square.width, square.height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        Canvas(out).drawOval(RectF(0f, 0f, square.width.toFloat(), square.height.toFloat()), paint)
        return out
    }

    suspend fun save(key: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val f = file(key) ?: return@withContext
        dir.mkdirs()
        val tmp = File(dir, "$key.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
        changed()
    }

    suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        if (file(key)?.delete() == true) changed()
    }

    private suspend fun move(from: String, to: String) = withContext(Dispatchers.IO) {
        val a = file(from)?.takeIf { it.isFile } ?: return@withContext
        val b = file(to) ?: return@withContext
        b.delete()
        if (!a.renameTo(b)) a.delete()
        changed()
    }

    /** Setup dialog saved: a new picture moves from its draft to [key]; REMOVE deletes [key]. */
    suspend fun apply(side: Side, change: PictureChange, key: String?) {
        when (change) {
            PictureChange.KEEP -> Unit
            PictureChange.NEW -> if (key != null) move(PictureRules.draft(side), key) else delete(PictureRules.draft(side))
            PictureChange.REMOVE -> key?.let { delete(it) }
        }
    }

    suspend fun clearDrafts() = prune(openSessionId = null, keepDrafts = false, nightsToo = false)

    /** Removes pictures of nights that are no longer open (and stale drafts / unknown files). */
    suspend fun prune(openSessionId: Long?, keepDrafts: Boolean, nightsToo: Boolean = true) = withContext(Dispatchers.IO) {
        var removed = false
        dir.listFiles()?.forEach { f ->
            val key = f.name.removeSuffix(".jpg")
            val night = key.startsWith("s") && PictureRules.isValid(key)
            val keep = if (night && !nightsToo) true else f.name.endsWith(".jpg") && PictureRules.keep(key, openSessionId, keepDrafts)
            if (!keep && f.delete()) removed = true
        }
        if (removed) changed()
    }

    /** After a restore: friend and night pictures may no longer match, so only "me" stays. */
    suspend fun keepOnlyMine() = withContext(Dispatchers.IO) {
        var removed = false
        dir.listFiles()?.forEach { f -> if (f.name != "${PictureRules.ME}.jpg" && f.delete()) removed = true }
        if (removed) changed()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dir.deleteRecursively()
        changed()
    }

    /**
     * Reads a picked photo at most [PictureRules.MAX_SOURCE_PX] on its long side.
     * API 28+: ImageDecoder (turns camera photos upright). API 26–27: BitmapFactory, as stored
     * (the framework EXIF reader has known security bugs there and the safe one would be a new dependency).
     */
    suspend fun decode(resolver: ContentResolver, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSampleSize(PictureRules.sampleSize(info.size.width, info.size.height, PictureRules.MAX_SOURCE_PX))
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                val options = BitmapFactory.Options().apply {
                    inSampleSize = PictureRules.sampleSize(bounds.outWidth, bounds.outHeight, PictureRules.MAX_SOURCE_PX)
                }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
        }.getOrNull()
    }

    /** The square under the circle, scaled to [PictureRules.OUTPUT_PX]. */
    fun crop(source: Bitmap, square: CropSquare): Bitmap {
        val size = PictureRules.OUTPUT_PX
        val out = createBitmap(size, size)
        val scale = size / square.size
        val matrix = Matrix().apply {
            postTranslate(-square.left, -square.top)
            postScale(scale, scale)
        }
        Canvas(out).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun changed() {
        cache.evictAll()
        _version.value++
    }

    private companion object {
        const val CACHE_ENTRIES = 8
        const val JPEG_QUALITY = 88
    }
}

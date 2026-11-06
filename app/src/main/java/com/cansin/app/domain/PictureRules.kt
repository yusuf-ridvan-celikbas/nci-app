package com.cansin.app.domain

import com.cansin.app.data.Side
import kotlin.math.max
import kotlin.math.min

/** Part of the source image to keep: a square [size] wide at ([left], [top]), in source pixels. */
data class CropSquare(val left: Float, val top: Float, val size: Float)

/** What the setup dialog did with one side's picture. */
enum class PictureChange { KEEP, NEW, REMOVE }

/**
 * P38: pictures behind +1 buttons. Files are named by owner key; nothing here touches files.
 * Keys: "me" (Tonight +1), "friend_<id>" (saved friends keep theirs), "s<session>_<SIDE>" (this night only),
 * "draft_<SIDE>" (picked in an open setup dialog, not saved yet).
 */
object PictureRules {
    const val ME = "me"
    const val OUTPUT_PX = 384
    const val WIDGET_PX = 192
    const val MAX_ZOOM = 5f
    const val MAX_SOURCE_PX = 2048

    private val KEY = Regex("me|friend_\\d+|s\\d+_[A-Z0-9_]+|draft_[A-Z0-9_]+")

    fun friend(id: Long) = "friend_$id"
    fun side(sessionId: Long, side: Side) = "s${sessionId}_${side.name}"
    fun draft(side: Side) = "draft_${side.name}"

    /** A linked friend's picture follows them; otherwise it belongs to this night's side. */
    fun keyFor(sessionId: Long?, side: Side, friendId: Long?): String? =
        friendId?.let(::friend) ?: sessionId?.let { side(it, side) }

    fun isValid(key: String) = KEY.matches(key)

    /**
     * Clean-up: night pictures live only while their night is open; drafts only while a dialog is open.
     * "me" and friends are kept until removed (or the friend is deleted).
     */
    fun keep(key: String, openSessionId: Long?, keepDrafts: Boolean): Boolean = when {
        !isValid(key) -> false
        key.startsWith("draft_") -> keepDrafts
        key.startsWith("s") -> openSessionId != null && key.startsWith("s${openSessionId}_")
        else -> true
    }

    /** Power-of-two sample size so the longer side ends up at most [maxPx]. */
    fun sampleSize(width: Int, height: Int, maxPx: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxPx) sample *= 2
        return sample
    }

    /** Scale that makes the image just cover a circle of [diameter] (zoom 1). */
    fun coverScale(width: Int, height: Int, diameter: Float): Float = diameter / min(width, height)

    fun clampZoom(zoom: Float) = zoom.coerceIn(1f, MAX_ZOOM)

    /** Largest offset (view px, from centre) that still keeps the circle covered. */
    fun maxOffset(width: Int, height: Int, diameter: Float, zoom: Float): Pair<Float, Float> {
        val k = coverScale(width, height, diameter) * clampZoom(zoom)
        return ((width * k - diameter) / 2).coerceAtLeast(0f) to ((height * k - diameter) / 2).coerceAtLeast(0f)
    }

    fun clampOffset(width: Int, height: Int, diameter: Float, zoom: Float, x: Float, y: Float): Pair<Float, Float> {
        val (mx, my) = maxOffset(width, height, diameter, zoom)
        return x.coerceIn(-mx, mx) to y.coerceIn(-my, my)
    }

    /**
     * The image is drawn centred, scaled by cover × [zoom], moved by ([x], [y]) view px.
     * Returns the source square under the circle.
     */
    fun crop(width: Int, height: Int, diameter: Float, zoom: Float, x: Float, y: Float): CropSquare {
        val z = clampZoom(zoom)
        val (cx, cy) = clampOffset(width, height, diameter, z, x, y)
        val k = coverScale(width, height, diameter) * z
        val size = (diameter / k).coerceAtMost(min(width, height).toFloat())
        val left = (width / 2f + (-diameter / 2 - cx) / k).coerceIn(0f, width - size)
        val top = (height / 2f + (-diameter / 2 - cy) / k).coerceIn(0f, height - size)
        return CropSquare(left, top, size)
    }
}

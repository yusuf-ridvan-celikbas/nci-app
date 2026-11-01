package com.cansin.app.system

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.cansin.app.domain.QuestionImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Reads a user-picked question CSV (system picker, no permission) and shares the template file. */
object QuestionFiles {
    private const val TEMPLATE = "salgambalgam-questions.csv"

    /** Null when the file can't be opened or is too big. UTF-8 first; Turkish Excel's older "CSV" (Windows-1254) as fallback. */
    suspend fun read(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readNBytesCompat(QuestionImport.MAX_BYTES + 1) }
        }.getOrNull() ?: return@withContext null
        if (bytes.size > QuestionImport.MAX_BYTES) return@withContext null
        decode(bytes, Charsets.UTF_8) ?: decode(bytes, Charset.forName("windows-1254"))
    }

    private fun decode(bytes: ByteArray, charset: Charset): String? = runCatching {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }.getOrNull()

    /** Writes the template (UTF-8 with BOM so Excel shows Turkish letters) and returns a share-sheet intent. */
    suspend fun templateIntent(context: Context, csv: String): Intent = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, TEMPLATE)
        file.writeText(QuestionImport.BOM + csv)
        val uri = FileProvider.getUriForFile(context, context.packageName + DataManager.AUTHORITY_SUFFIX, file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(TEMPLATE, uri)
        Intent.createChooser(send, null)
    }
}

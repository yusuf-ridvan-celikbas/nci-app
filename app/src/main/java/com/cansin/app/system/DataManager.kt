package com.cansin.app.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.cansin.app.data.AppDatabase
import com.cansin.app.domain.ExportData
import com.cansin.app.domain.ExportJson
import com.cansin.app.domain.RestoreData
import com.cansin.app.domain.RestoreError
import com.cansin.app.domain.RestoreParser
import com.cansin.app.domain.RestoreResult
import com.cansin.app.domain.TimerState
import com.cansin.app.data.FriendProfile
import androidx.room.withTransaction
import com.cansin.app.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** InputStream.readNBytes is API 33+; minSdk is 26. */
private fun InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (out.size() < max) {
        val n = read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

class DataManager(
    private val context: Context,
    private val database: AppDatabase,
    private val store: SettingsStore,
    private val cancelReminders: suspend () -> Unit,
    private val refreshWidgets: suspend () -> Unit,
) {
    private val exportDir get() = File(context.cacheDir, EXPORT_DIR)

    suspend fun export(includeProfile: Boolean): Intent = withContext(Dispatchers.IO) {
        val dao = database.exportDao()
        val settings = store.settings.first()
        val data = ExportData(
            dao.sessions(), dao.beers(), dao.water(), dao.notes(), dao.friends(),
            settings.profile,
            friendResults = database.friendResultDao().all(),
            currency = settings.currency,
            seats = database.groupSeatDao().all(),
        )
        val json = ExportJson.build(data, includeProfile, System.currentTimeMillis())
        exportDir.deleteRecursively()
        exportDir.mkdirs()
        val file = File(exportDir, EXPORT_FILE)
        file.writeText(json)
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME_JSON)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = android.content.ClipData.newRawUri(EXPORT_FILE, uri)
        Intent.createChooser(send, null)
    }

    /** Reads a picked file (capped, strict UTF-8) and checks it; nothing is changed yet. */
    suspend fun readBackup(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readNBytesCompat(RestoreParser.MAX_BYTES + 1) }
        }.getOrNull() ?: return@withContext RestoreResult.Fail(RestoreError.INVALID)
        if (bytes.size > RestoreParser.MAX_BYTES) return@withContext RestoreResult.Fail(RestoreError.TOO_LARGE)
        val text = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrNull()?.let { if (it.firstOrNull()?.code == BOM) it.drop(1) else it } ?: return@withContext RestoreResult.Fail(RestoreError.INVALID)
        withContext(Dispatchers.Default) { RestoreParser.parse(text, System.currentTimeMillis()) }
    }

    /** Replaces all sessions, friends and friend results in one transaction. Settings and PIN stay. */
    suspend fun restore(data: RestoreData) {
        cancelReminders()
        store.setTimer(TimerState())
        withContext(Dispatchers.IO) {
            val dao = database.restoreDao()
            database.withTransaction {
                dao.clearSessions()
                dao.clearFriends()
                dao.clearFriendResults()
                val friendIds = data.friends.associate { it.fileId to dao.insert(FriendProfile(name = it.name, color = 0)) }
                data.sessions.forEach { r ->
                    val s = r.session
                    val id = dao.insert(s.copy(redFriendId = s.redFriendId?.let(friendIds::get), blueFriendId = s.blueFriendId?.let(friendIds::get)))
                    dao.insertBeers(r.beers.map { it.copy(sessionId = id) })
                    dao.insertWater(r.water.map { it.copy(sessionId = id) })
                    dao.insertNotes(r.notes.map { it.copy(sessionId = id) })
                    dao.insertSeats(r.seats.map { it.copy(sessionId = id) })
                }
                dao.insertFriendResults(data.friendResults)
            }
            exportDir.deleteRecursively()
        }
        data.currency?.let { store.setCurrency(it) }
        data.profile?.let { store.setProfile(it) }
        refreshWidgets()
    }

    suspend fun deleteAll() {
        cancelReminders()
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            exportDir.deleteRecursively()
        }
        store.clearUserData()
        refreshWidgets()
    }

    companion object {
        private const val BOM = 0xFEFF
        private const val EXPORT_DIR = "export"
        private const val EXPORT_FILE = "cansin-export.json"
        private const val MIME_JSON = "application/json"
        const val AUTHORITY_SUFFIX = ".export"
    }
}

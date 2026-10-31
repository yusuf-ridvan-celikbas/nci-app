package com.cansin.app.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.cansin.app.data.AppDatabase
import com.cansin.app.domain.ExportData
import com.cansin.app.domain.ExportJson
import com.cansin.app.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

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
            database.friendResultDao().all(),
            settings.currency,
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
        private const val EXPORT_DIR = "export"
        private const val EXPORT_FILE = "cansin-export.json"
        private const val MIME_JSON = "application/json"
        const val AUTHORITY_SUFFIX = ".export"
    }
}

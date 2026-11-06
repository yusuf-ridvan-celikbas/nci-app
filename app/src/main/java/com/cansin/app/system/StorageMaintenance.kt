package com.cansin.app.system

import android.content.Context
import androidx.room.withTransaction
import com.cansin.app.data.AppDatabase
import com.cansin.app.domain.CompressAfter
import com.cansin.app.domain.KeepHistory
import com.cansin.app.domain.StorageRules
import com.cansin.app.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/** What the app keeps on the phone (P27). Bytes are files on disk; counts are rows. */
data class StorageUsage(
    val databaseBytes: Long,
    val otherBytes: Long,
    val nights: Int,
    val compressed: Int,
    val questions: Int,
) {
    val totalBytes: Long get() = databaseBytes + otherBytes
}

data class CleanResult(val deleted: Int, val compressed: Int, val freedBytes: Long)

/**
 * P27 storage upkeep, all offline:
 * - deletes ended nights older than "Keep history",
 * - compresses ended nights older than "Compress old nights" (totals kept, drink/water/note rows removed),
 * - removes leftover shared files in cache/export,
 * - checkpoints the WAL journal and VACUUMs when there is space to win.
 * Runs at most once a day on app start, or right away from Settings. Writes go through [exclusive]
 * (the session repository's write lock) so a +1 never races it. The open night is never touched.
 */
class StorageMaintenance(
    private val context: Context,
    private val database: AppDatabase,
    private val store: SettingsStore,
    private val exclusive: suspend (suspend () -> Unit) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao get() = database.storageDao()

    /** P36: clear stored brands and remove brand names from notes / own questions written before. */
    suspend fun scrubBrands() = withContext(Dispatchers.IO) {
        dao.clearBrands()
        dao.allNotes().forEach { n ->
            val clean = com.cansin.app.domain.BrandFilter.strip(n.text)
            if (clean != n.text) dao.setNoteText(n.id, clean.ifEmpty { "–" })
        }
        dao.allQuestions().forEach { q ->
            val clean = com.cansin.app.domain.BrandFilter.strip(q.text)
            if (clean.isEmpty()) dao.deleteQuestion(q.id) else if (clean != q.text) dao.setQuestionText(q.id, clean)
        }
    }

    suspend fun runIfDue() {
        if (StorageRules.isDue(store.maintenanceAt(), clock())) run()
    }

    suspend fun run(): CleanResult = withContext(Dispatchers.IO) {
        val before = fileBytes()
        val now = clock()
        val settings = store.settings.first()
        var deleted = 0
        var compressed = 0
        exclusive {
            StorageRules.cutoff(now, settings.keepHistory.months)?.let { deleted = dao.deleteEndedBefore(it) }
            StorageRules.cutoff(now, settings.compressAfter.months)?.let { cut ->
                dao.uncompressedBefore(cut).forEach { session ->
                    database.withTransaction {
                        val totals = StorageRules.summarize(
                            session,
                            dao.beers(session.id),
                            dao.water(session.id),
                            dao.notes(session.id),
                            settings.profile,
                            now,
                        )
                        dao.update(session.copy(totals = totals))
                        dao.deleteBeers(session.id)
                        dao.deleteWater(session.id)
                        dao.deleteNotes(session.id)
                    }
                    compressed++
                }
            }
            cleanCache()
            compact(force = deleted + compressed > 0)
        }
        store.setMaintenanceAt(now)
        CleanResult(deleted, compressed, (before - fileBytes()).coerceAtLeast(0))
    }

    /** "Free up space" button: no data is removed, only temporary files and unused database space. */
    suspend fun freeUpSpace(): Long = withContext(Dispatchers.IO) {
        val before = fileBytes()
        exclusive {
            cleanCache()
            compact(force = true)
        }
        (before - fileBytes()).coerceAtLeast(0)
    }

    /** After delete-all / restore: give the freed space back to the phone. */
    suspend fun compactNow() = withContext(Dispatchers.IO) { compact(force = true) }

    suspend fun usage(): StorageUsage = withContext(Dispatchers.IO) {
        StorageUsage(
            databaseBytes = dbFiles().sumOf { it.length() },
            otherBytes = dirBytes(File(context.filesDir, DATASTORE_DIR)) + dirBytes(exportDir),
            nights = dao.countEnded(),
            compressed = dao.countCompressed(),
            questions = dao.countQuestions(),
        )
    }

    /** How many nights a new choice would delete / compress right now (for the confirm dialog). */
    suspend fun wouldDelete(keep: KeepHistory): Int =
        StorageRules.cutoff(clock(), keep.months)?.let { dao.countEndedBefore(it) } ?: 0

    suspend fun wouldCompress(after: CompressAfter): Int =
        StorageRules.cutoff(clock(), after.months)?.let { dao.countUncompressedBefore(it) } ?: 0

    private fun compact(force: Boolean) {
        val db = database.openHelper.writableDatabase
        checkpoint(db)
        val pages = db.query("PRAGMA page_count").use { if (it.moveToFirst()) it.getLong(0) else 0L }
        val free = db.query("PRAGMA freelist_count").use { if (it.moveToFirst()) it.getLong(0) else 0L }
        if (force || (pages > 0 && free * FREE_RATIO_DIV > pages)) {
            db.execSQL("VACUUM")
            checkpoint(db)
        }
    }

    private fun checkpoint(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
    }

    private val exportDir get() = File(context.cacheDir, EXPORT_DIR)

    private fun cleanCache() {
        exportDir.deleteRecursively()
    }

    private fun dbFiles(): List<File> {
        val main = context.getDatabasePath(DB_NAME)
        return listOf(main, File(main.path + "-wal"), File(main.path + "-shm"), File(main.path + "-journal")).filter { it.exists() }
    }

    private fun fileBytes(): Long = dbFiles().sumOf { it.length() } + dirBytes(exportDir)

    private fun dirBytes(dir: File): Long = if (!dir.exists()) 0 else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    companion object {
        const val DB_NAME = "cansin.db"
        private const val EXPORT_DIR = "export"
        private const val DATASTORE_DIR = "datastore"
        /** VACUUM on its own when more than 1/4 of the pages are free. */
        private const val FREE_RATIO_DIV = 4
    }
}

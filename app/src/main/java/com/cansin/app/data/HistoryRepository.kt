package com.cansin.app.data

import com.cansin.app.domain.ResultCard
import kotlinx.coroutines.flow.Flow

enum class ImportResult { ADDED, DUPLICATE }

class HistoryRepository(
    private val sessions: SessionDao,
    private val beers: BeerDao,
    private val notes: NoteDao,
    private val water: WaterDao,
    private val friendResults: FriendResultDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val summaries: Flow<List<SessionSummary>> = sessions.observeHistory()
    val endedBeers: Flow<List<BeerEvent>> = beers.observeEnded()
    val friendResultList: Flow<List<FriendResult>> = friendResults.observeAll()

    fun session(id: Long): Flow<Session?> = sessions.observe(id)
    fun beers(id: Long): Flow<List<BeerEvent>> = beers.observe(id)
    fun notes(id: Long): Flow<List<Note>> = notes.observe(id)
    fun water(id: Long): Flow<List<WaterEvent>> = water.observe(id)

    suspend fun delete(id: Long): Boolean = sessions.deleteEnded(id) > 0

    suspend fun importResult(card: ResultCard): ImportResult =
        if (friendResults.insert(FriendResult.of(card, clock())) == -1L) ImportResult.DUPLICATE else ImportResult.ADDED

    suspend fun deleteFriendResult(id: Long) = friendResults.delete(id)
}

package com.cansin.app.data

import com.cansin.app.domain.ResultCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ImportResult { ADDED, DUPLICATE }

class HistoryRepository(
    private val sessions: SessionDao,
    private val beers: BeerDao,
    private val notes: NoteDao,
    private val water: WaterDao,
    private val friendResults: FriendResultDao,
    private val groupSeats: GroupSeatDao = NoSeats,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val summaries: Flow<List<SessionSummary>> = sessions.observeHistory()
    val endedBeers: Flow<List<BeerEvent>> = beers.observeEnded()
    val friendResultList: Flow<List<FriendResult>> = friendResults.observeAll()

    fun session(id: Long): Flow<Session?> = sessions.observe(id)
    fun beers(id: Long): Flow<List<BeerEvent>> = beers.observe(id)
    fun notes(id: Long): Flow<List<Note>> = notes.observe(id)
    fun water(id: Long): Flow<List<WaterEvent>> = water.observe(id)
    fun seats(id: Long): Flow<List<GroupSeat>> = groupSeats.observe(id)

    suspend fun delete(id: Long): Boolean = sessions.deleteEnded(id) > 0

    /** P30: delete many ended nights; chunked to stay under SQLite's variable limit. */
    suspend fun deleteMany(ids: Collection<Long>): Int = ids.distinct().chunked(DELETE_CHUNK).sumOf { sessions.deleteEnded(it) }

    /** P30: all searchable text per night, joined. */
    val searchText: Flow<Map<Long, String>> = sessions.observeSearchText().map { rows ->
        rows.groupBy({ it.sessionId }, { it.text }).mapValues { (_, texts) -> texts.joinToString(" ") }
    }

    companion object {
        const val DELETE_CHUNK = 500
    }

    suspend fun importResult(card: ResultCard): ImportResult =
        if (friendResults.insert(FriendResult.of(card, clock())) == -1L) ImportResult.DUPLICATE else ImportResult.ADDED

    suspend fun deleteFriendResult(id: Long) = friendResults.delete(id)
}

package com.cansin.app.data

import kotlinx.coroutines.flow.Flow

class HistoryRepository(
    private val sessions: SessionDao,
    private val beers: BeerDao,
    private val notes: NoteDao,
    private val water: WaterDao,
) {
    val summaries: Flow<List<SessionSummary>> = sessions.observeHistory()
    val endedBeers: Flow<List<BeerEvent>> = beers.observeEnded()

    fun session(id: Long): Flow<Session?> = sessions.observe(id)
    fun beers(id: Long): Flow<List<BeerEvent>> = beers.observe(id)
    fun notes(id: Long): Flow<List<Note>> = notes.observe(id)
    fun water(id: Long): Flow<List<WaterEvent>> = water.observe(id)

    suspend fun delete(id: Long): Boolean = sessions.deleteEnded(id) > 0
}

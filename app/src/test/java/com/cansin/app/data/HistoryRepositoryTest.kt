package com.cansin.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryRepositoryTest {
    private val sessions = FakeSessionDao()
    private val repo = HistoryRepository(sessions, FakeBeerDao(), FakeNoteDao(), FakeWaterDao(), FakeFriendResultDao())

    @Test
    fun deleteManyRemovesOnlyEndedNightsInChunks() = runBlocking {
        repeat(1_201) { sessions.insert(Session(startedAt = it.toLong(), endedAt = it + 1L)) }
        val open = sessions.insert(Session(startedAt = 5_000))
        val ids = sessions.rows.value.map { it.id }
        assertEquals(1_201, repo.deleteMany(ids + ids.take(3)))
        assertEquals(listOf(open), sessions.rows.value.map { it.id })
        assertEquals(3, sessions.deleteCalls) // 1 202 ids → 500 + 500 + 202
    }

    @Test
    fun searchTextIsJoinedPerNight() = runBlocking {
        sessions.searchRows.value = listOf(SearchRow(1, "Ali Can"), SearchRow(1, "Efes"), SearchRow(2, "nuts"))
        assertEquals(mapOf(1L to "Ali Can Efes", 2L to "nuts"), repo.searchText.first())
    }
}

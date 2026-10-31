package com.cansin.app.data

import com.cansin.app.domain.SessionRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaterRepositoryTest {
    private val sessions = FakeSessionDao()
    private val beers = FakeBeerDao()
    private val notes = FakeNoteDao()
    private val water = FakeWaterDao()
    private var now = 1_000_000L
    private val repo = SessionRepository(sessions, beers, notes, water, clock = { now })

    @Test
    fun waterNeedsOpenSession() = runBlocking {
        assertNull(repo.addWater())
        assertEquals(0, sessions.rows.value.size)
    }

    @Test
    fun waterJoinsSessionAndKeepsItAlive() = runBlocking {
        val beer = repo.add()
        now += SessionRules.DEFAULT_INACTIVITY_MS - 1_000
        repo.addWater()
        now += 2_000
        assertEquals(beer.sessionId, repo.add().sessionId)
    }

    @Test
    fun sessionWithWaterSurvivesBeerUndo() = runBlocking {
        val beer = repo.add()
        repo.addWater()
        repo.undo(beer)
        assertEquals(beer.sessionId, repo.openSession.first()?.id)
    }

    @Test
    fun undoingLastWaterOfEmptySessionDeletesIt() = runBlocking {
        val beer = repo.add()
        val glass = repo.addWater()!!
        repo.undo(beer)
        repo.undoWater(glass)
        assertEquals(0, sessions.rows.value.size)
    }
}

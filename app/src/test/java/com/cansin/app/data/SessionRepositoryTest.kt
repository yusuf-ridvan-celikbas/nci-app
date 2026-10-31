package com.cansin.app.data

import com.cansin.app.domain.SessionRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionRepositoryTest {
    private val sessions = FakeSessionDao()
    private val beers = FakeBeerDao()
    private var now = 1_000_000L
    private val repo = SessionRepository(sessions, beers, clock = { now })

    private suspend fun openCount() = repo.openSession.first()?.let { beers.count(it.id) } ?: 0

    @Test
    fun firstTapStartsSessionAndLaterTapsReuseIt() = runBlocking {
        val first = repo.addBeer()
        now += 60_000
        val second = repo.addBeer()
        assertEquals(first.sessionId, second.sessionId)
        assertEquals(1, sessions.rows.value.size)
        assertEquals(2, openCount())
    }

    @Test
    fun removeLatestWithoutSessionIsNoOp() = runBlocking {
        repo.removeLatest()
        assertEquals(0, openCount())
        assertEquals(0, sessions.rows.value.size)
    }

    @Test
    fun removingLastBeerDeletesEmptySession() = runBlocking {
        repo.addBeer()
        repo.removeLatest()
        repo.removeLatest()
        assertNull(repo.openSession.first())
        assertEquals(0, sessions.rows.value.size)
        assertEquals(0, beers.rows.value.size)
    }

    @Test
    fun removeLatestRemovesNewestOnly() = runBlocking {
        repo.addBeer()
        now += 1_000
        val newest = repo.addBeer()
        repo.removeLatest()
        assertEquals(1, openCount())
        assertEquals(false, beers.rows.value.any { it.id == newest.id })
    }

    @Test
    fun undoIsIdempotent() = runBlocking {
        repo.addBeer()
        val event = repo.addBeer()
        repo.undo(event)
        repo.undo(event)
        assertEquals(1, openCount())
    }

    @Test
    fun undoOfOnlyBeerDeletesSession() = runBlocking {
        repo.undo(repo.addBeer())
        assertEquals(0, sessions.rows.value.size)
    }

    @Test
    fun staleSessionClosesAtLastBeerAndNewTapStartsNewSession() = runBlocking {
        val old = repo.addBeer()
        now += SessionRules.DEFAULT_INACTIVITY_MS
        val fresh = repo.addBeer()
        assertNotEquals(old.sessionId, fresh.sessionId)
        assertEquals(old.at, sessions.rows.value.first { it.id == old.sessionId }.endedAt)
        assertEquals(1, openCount())
    }

    @Test
    fun closeIfStaleEndsInactiveSession() = runBlocking {
        val beer = repo.addBeer()
        now += SessionRules.DEFAULT_INACTIVITY_MS - 1
        repo.closeIfStale()
        assertEquals(beer.sessionId, repo.openSession.first()?.id)
        now += 1
        repo.closeIfStale()
        assertNull(repo.openSession.first())
    }

    @Test
    fun removeLatestDoesNotTouchStaleSession() = runBlocking {
        repo.addBeer()
        now += SessionRules.DEFAULT_INACTIVITY_MS
        repo.removeLatest()
        assertEquals(1, beers.rows.value.size)
    }

    @Test
    fun endSessionSetsEndTime() = runBlocking {
        val beer = repo.addBeer()
        now += 5_000
        repo.endSession()
        assertEquals(now, sessions.rows.value.first { it.id == beer.sessionId }.endedAt)
        assertNull(repo.openSession.first())
    }

    @Test
    fun snapshotReflectsOpenSession() = runBlocking {
        assertEquals(CounterSnapshot(0, null), repo.snapshot())
        repo.addBeer()
        now += 1_000
        val last = repo.addBeer()
        assertEquals(CounterSnapshot(2, last.at), repo.snapshot())
    }

    @Test
    fun snapshotTreatsStaleSessionAsEmptyWithoutWriting() = runBlocking {
        repo.addBeer()
        now += SessionRules.DEFAULT_INACTIVITY_MS
        assertEquals(CounterSnapshot(0, null), repo.snapshot())
        assertNull(sessions.rows.value.single().endedAt)
    }

    @Test
    fun everyWriteNotifiesChange() = runBlocking {
        var changes = 0
        val notifying = SessionRepository(sessions, beers, clock = { now }, onChange = { changes++ })
        val beer = notifying.addBeer()
        notifying.undo(beer)
        notifying.removeLatest()
        notifying.endSession()
        assertEquals(4, changes)
    }

    @Test
    fun concurrentTapsAreSerialized() = runBlocking(Dispatchers.Default) {
        (1..200).map { async { repo.addBeer() } }.awaitAll()
        assertEquals(1, sessions.rows.value.size)
        assertEquals(200, openCount())
    }
}

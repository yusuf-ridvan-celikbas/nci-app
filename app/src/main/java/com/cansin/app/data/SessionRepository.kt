package com.cansin.app.data

import com.cansin.app.domain.SessionRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CounterSnapshot(val count: Int, val lastBeerAt: Long?)

class SessionRepository(
    private val sessions: SessionDao,
    private val beers: BeerDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val inactivityMs: Long = SessionRules.DEFAULT_INACTIVITY_MS,
    private val onChange: suspend () -> Unit = {},
) {
    private val writeLock = Mutex()

    val openSession: Flow<Session?> = sessions.observeOpen()

    fun beerCount(sessionId: Long): Flow<Int> = beers.observeCount(sessionId)

    suspend fun snapshot(): CounterSnapshot {
        val session = sessions.open() ?: return CounterSnapshot(0, null)
        val latest = beers.latest(session.id)
        if (latest == null || SessionRules.isStale(latest.at, clock(), inactivityMs)) return CounterSnapshot(0, null)
        return CounterSnapshot(beers.count(session.id), latest.at)
    }

    suspend fun addBeer(): BeerEvent = write {
        val now = clock()
        val sessionId = activeSession(now)?.id ?: sessions.insert(Session(startedAt = now))
        val event = BeerEvent(sessionId = sessionId, at = now)
        event.copy(id = beers.insert(event))
    }

    suspend fun undo(event: BeerEvent) = write {
        if (beers.delete(event.id) > 0) deleteIfEmpty(event.sessionId)
    }

    suspend fun removeLatest() = write {
        val session = activeSession(clock()) ?: return@write
        val latest = beers.latest(session.id) ?: return@write
        beers.delete(latest.id)
        deleteIfEmpty(session.id)
    }

    suspend fun endSession() = write {
        val session = sessions.open() ?: return@write
        if (beers.count(session.id) == 0) sessions.delete(session.id) else sessions.end(session.id, clock())
    }

    suspend fun closeIfStale() = write { activeSession(clock()) }

    private suspend fun <T> write(block: suspend () -> T): T {
        val result = writeLock.withLock { block() }
        onChange()
        return result
    }

    private suspend fun activeSession(now: Long): Session? {
        val session = sessions.open() ?: return null
        val lastActivity = beers.latest(session.id)?.at ?: session.startedAt
        if (!SessionRules.isStale(lastActivity, now, inactivityMs)) return session
        sessions.end(session.id, lastActivity)
        return null
    }

    private suspend fun deleteIfEmpty(sessionId: Long) {
        if (beers.count(sessionId) == 0) sessions.delete(sessionId)
    }
}

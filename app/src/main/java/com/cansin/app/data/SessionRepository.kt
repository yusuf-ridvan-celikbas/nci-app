package com.cansin.app.data

import com.cansin.app.domain.SessionRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionRepository(
    private val sessions: SessionDao,
    private val beers: BeerDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val inactivityMs: Long = SessionRules.DEFAULT_INACTIVITY_MS,
) {
    private val writeLock = Mutex()

    val openSession: Flow<Session?> = sessions.observeOpen()

    fun beerCount(sessionId: Long): Flow<Int> = beers.observeCount(sessionId)

    suspend fun addBeer(): BeerEvent = writeLock.withLock {
        val now = clock()
        val sessionId = activeSession(now)?.id ?: sessions.insert(Session(startedAt = now))
        val event = BeerEvent(sessionId = sessionId, at = now)
        event.copy(id = beers.insert(event))
    }

    suspend fun undo(event: BeerEvent) = writeLock.withLock {
        if (beers.delete(event.id) > 0) deleteIfEmpty(event.sessionId)
    }

    suspend fun removeLatest() = writeLock.withLock {
        val session = activeSession(clock()) ?: return@withLock
        val latest = beers.latest(session.id) ?: return@withLock
        beers.delete(latest.id)
        deleteIfEmpty(session.id)
    }

    suspend fun endSession() = writeLock.withLock {
        val session = sessions.open() ?: return@withLock
        if (beers.count(session.id) == 0) sessions.delete(session.id) else sessions.end(session.id, clock())
    }

    suspend fun closeIfStale() = writeLock.withLock { activeSession(clock()) }

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

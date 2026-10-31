package com.cansin.app.data

import com.cansin.app.domain.SessionRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CounterSnapshot(val count: Int, val lastBeerAt: Long?, val sessionId: Long? = null)

interface SessionListener {
    suspend fun onChanged() {}
    suspend fun onBeerAdded(event: BeerEvent) {}
    suspend fun onSessionClosed(sessionId: Long) {}
}

class SessionRepository(
    private val sessions: SessionDao,
    private val beers: BeerDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val inactivityMs: Long = SessionRules.DEFAULT_INACTIVITY_MS,
    private val listener: SessionListener = object : SessionListener {},
) {
    private sealed interface Event {
        data class BeerAdded(val event: BeerEvent) : Event
        data class SessionClosed(val sessionId: Long) : Event
    }

    private val writeLock = Mutex()
    private val pending = mutableListOf<Event>()

    val openSession: Flow<Session?> = sessions.observeOpen()

    fun beerCount(sessionId: Long): Flow<Int> = beers.observeCount(sessionId)

    suspend fun snapshot(): CounterSnapshot {
        val session = sessions.open() ?: return CounterSnapshot(0, null)
        val latest = beers.latest(session.id)
        if (latest == null || SessionRules.isStale(latest.at, clock(), inactivityMs)) return CounterSnapshot(0, null)
        return CounterSnapshot(beers.count(session.id), latest.at, session.id)
    }

    suspend fun addBeer(finishPrevious: Boolean = false): BeerEvent = write {
        val now = clock()
        val active = activeSession(now)
        if (finishPrevious && active != null) beers.latest(active.id)?.let { beers.finish(it.id, now) }
        val sessionId = active?.id ?: sessions.insert(Session(startedAt = now))
        val event = BeerEvent(sessionId = sessionId, at = now)
        event.copy(id = beers.insert(event)).also { pending += Event.BeerAdded(it) }
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
        pending += Event.SessionClosed(session.id)
    }

    suspend fun closeIfStale() = write { activeSession(clock()) }

    private suspend fun <T> write(block: suspend () -> T): T {
        val (result, events) = writeLock.withLock {
            val r = block()
            r to pending.toList().also { pending.clear() }
        }
        for (e in events) {
            when (e) {
                is Event.BeerAdded -> listener.onBeerAdded(e.event)
                is Event.SessionClosed -> listener.onSessionClosed(e.sessionId)
            }
        }
        listener.onChanged()
        return result
    }

    private suspend fun activeSession(now: Long): Session? {
        val session = sessions.open() ?: return null
        val lastActivity = beers.latest(session.id)?.at ?: session.startedAt
        if (!SessionRules.isStale(lastActivity, now, inactivityMs)) return session
        sessions.end(session.id, lastActivity)
        pending += Event.SessionClosed(session.id)
        return null
    }

    private suspend fun deleteIfEmpty(sessionId: Long) {
        if (beers.count(sessionId) > 0) return
        sessions.delete(sessionId)
        pending += Event.SessionClosed(sessionId)
    }
}

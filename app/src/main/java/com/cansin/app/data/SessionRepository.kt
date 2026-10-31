package com.cansin.app.data

import com.cansin.app.domain.BeerRules
import com.cansin.app.domain.BeerSize
import com.cansin.app.domain.NoteRules
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
    private val notes: NoteDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val inactivityMs: Long = SessionRules.DEFAULT_INACTIVITY_MS,
    private val listener: SessionListener = object : SessionListener {},
    private val defaultSize: suspend () -> BeerSize = { BeerSize.DEFAULT },
) {
    private sealed interface Event {
        data class BeerAdded(val event: BeerEvent) : Event
        data class SessionClosed(val sessionId: Long) : Event
    }

    private val writeLock = Mutex()
    private val pending = mutableListOf<Event>()

    val openSession: Flow<Session?> = sessions.observeOpen()

    fun beerCount(sessionId: Long): Flow<Int> = beers.observeCount(sessionId)

    fun notes(sessionId: Long): Flow<List<Note>> = notes.observe(sessionId)

    suspend fun snapshot(): CounterSnapshot {
        val session = sessions.open() ?: return CounterSnapshot(0, null)
        val latest = beers.latest(session.id)
        if (latest == null || SessionRules.isStale(latest.at, clock(), inactivityMs)) return CounterSnapshot(0, null)
        return CounterSnapshot(beers.count(session.id), latest.at, session.id)
    }

    suspend fun addBeer(finishPrevious: Boolean = false, size: BeerSize? = null): BeerEvent {
        val chosen = BeerRules.clamp(size ?: defaultSize())
        return write { insertBeer(finishPrevious, chosen) }
    }

    private suspend fun insertBeer(finishPrevious: Boolean, size: BeerSize): BeerEvent {
        val now = clock()
        val active = activeSession(now)
        if (finishPrevious && active != null) beers.latest(active.id)?.let { beers.finish(it.id, now) }
        val sessionId = active?.id ?: sessions.insert(Session(startedAt = now))
        val event = BeerEvent(sessionId = sessionId, sizeMl = size.sizeMl, abv = size.abv, at = now)
        return event.copy(id = beers.insert(event)).also { pending += Event.BeerAdded(it) }
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
        if (isEmpty(session.id)) sessions.delete(session.id) else sessions.end(session.id, clock())
        pending += Event.SessionClosed(session.id)
    }

    suspend fun addNote(text: String, amountMinor: Long?): Note? = write {
        val clean = NoteRules.cleanText(text) ?: return@write null
        val now = clock()
        val sessionId = activeSession(now)?.id ?: sessions.insert(Session(startedAt = now))
        val note = Note(sessionId = sessionId, text = clean, amountMinor = amountMinor?.takeIf { NoteRules.isValidAmount(it) }, createdAt = now)
        note.copy(id = notes.insert(note))
    }

    suspend fun updateNote(note: Note) = write {
        val clean = NoteRules.cleanText(note.text) ?: return@write
        notes.update(
            note.copy(
                text = clean,
                person = NoteRules.cleanPerson(note.person),
                amountMinor = note.amountMinor?.takeIf { NoteRules.isValidAmount(it) },
            ),
        )
    }

    suspend fun deleteNote(note: Note) = write {
        notes.delete(note.id)
        deleteIfEmpty(note.sessionId)
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
        val lastActivity = maxOf(session.startedAt, beers.latest(session.id)?.at ?: 0, notes.lastCreatedAt(session.id) ?: 0)
        if (!SessionRules.isStale(lastActivity, now, inactivityMs)) return session
        sessions.end(session.id, lastActivity)
        pending += Event.SessionClosed(session.id)
        return null
    }

    private suspend fun deleteIfEmpty(sessionId: Long) {
        if (!isEmpty(sessionId)) return
        sessions.delete(sessionId)
        pending += Event.SessionClosed(sessionId)
    }

    private suspend fun isEmpty(sessionId: Long) = beers.count(sessionId) == 0 && notes.count(sessionId) == 0
}

package com.cansin.app.data

import com.cansin.app.domain.BeerRules
import com.cansin.app.domain.BeerSize
import com.cansin.app.domain.DrinkChoice
import com.cansin.app.domain.GroupRules
import com.cansin.app.domain.GroupSetup
import com.cansin.app.domain.NoteRules
import com.cansin.app.domain.SessionRules
import com.cansin.app.domain.VersusRules
import com.cansin.app.domain.VersusSetup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CounterSnapshot(
    val count: Int,
    val lastBeerAt: Long?,
    val sessionId: Long? = null,
    val versus: Boolean = false,
    val red: Int = 0,
    val blue: Int = 0,
    val notes: Int = 0,
    val tabMinor: Long = 0,
    val water: Int = 0,
    val hasMe: Boolean = false,
    val myLatestBeer: BeerEvent? = null,
    val group: Boolean = false,
)

interface SessionListener {
    suspend fun onChanged() {}
    suspend fun onBeerAdded(event: BeerEvent, mine: Boolean) {}
    suspend fun onSessionClosed(sessionId: Long) {}
}

/** For callers (tests, old wiring) without group support: no seats, so group adds are refused. */
object NoSeats : GroupSeatDao {
    override suspend fun seats(sessionId: Long) = emptyList<GroupSeat>()
    override fun observe(sessionId: Long): Flow<List<GroupSeat>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun all() = emptyList<GroupSeat>()
    override suspend fun insert(seats: List<GroupSeat>) {}
    override suspend fun clear(sessionId: Long) {}
}

class SessionRepository(
    private val sessions: SessionDao,
    private val beers: BeerDao,
    private val notes: NoteDao,
    private val water: WaterDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val inactivityMs: Long = SessionRules.DEFAULT_INACTIVITY_MS,
    private val listener: SessionListener = object : SessionListener {},
    private val defaultDrink: suspend () -> DrinkChoice = { DrinkChoice.DEFAULT },
    private val seats: GroupSeatDao = NoSeats,
    /** P28: price per drink kind from Settings (null = not on the Tab); saved on the drink when added. */
    private val price: suspend (DrinkKind) -> Long? = { null },
) {
    private sealed interface Event {
        data class BeerAdded(val event: BeerEvent, val mine: Boolean) : Event
        data class SessionClosed(val sessionId: Long) : Event
    }

    private val writeLock = Mutex()
    private val pending = mutableListOf<Event>()

    val openSession: Flow<Session?> = sessions.observeOpen()

    fun beerCount(sessionId: Long): Flow<Int> = beers.observeCount(sessionId)

    fun notes(sessionId: Long): Flow<List<Note>> = notes.observe(sessionId)

    fun beers(sessionId: Long): Flow<List<BeerEvent>> = beers.observe(sessionId)

    fun water(sessionId: Long): Flow<List<WaterEvent>> = water.observe(sessionId)

    suspend fun snapshot(): CounterSnapshot {
        val session = sessions.open() ?: return CounterSnapshot(0, null)
        if (SessionRules.isStale(lastActivity(session), clock(), inactivityMs)) return CounterSnapshot(0, null)
        val versus = session.mode == SessionMode.VERSUS
        val group = session.mode == SessionMode.GROUP
        val me = VersusRules.meSide(session)
        return CounterSnapshot(
            count = if (group && me != null) beers.countOnSide(session.id, me) else beers.count(session.id),
            lastBeerAt = beers.latest(session.id)?.at,
            sessionId = session.id,
            versus = versus,
            red = if (versus) beers.countOnSide(session.id, Side.RED) else 0,
            blue = if (versus) beers.countOnSide(session.id, Side.BLUE) else 0,
            notes = notes.count(session.id),
            tabMinor = notes.totalMinor(session.id) + beers.totalPriceMinor(session.id),
            water = if (group) me?.let { water.countOnSide(session.id, it) } ?: 0 else water.count(session.id),
            hasMe = me != null,
            myLatestBeer = me?.let { beers.latestOnSide(session.id, it) },
            group = group,
        )
    }

    suspend fun finishBeer(beerId: Long, sessionId: Long) = write {
        val session = activeSession(clock())?.takeIf { it.id == sessionId } ?: return@write
        beers.finish(beerId, clock())
        session
    }

    /** [size] alone keeps the default kind (old long-press path); [drink] sets both. */
    suspend fun addBeer(finishPrevious: Boolean = false, size: BeerSize? = null, side: Side = Side.SOLO, drink: DrinkChoice? = null): BeerEvent? {
        val base = drink ?: defaultDrink().let { d -> size?.let { d.copy(size = it) } ?: d }
        val chosen = base.copy(size = BeerRules.clamp(base.size))
        return write { insertBeer(finishPrevious, chosen) { side } }
    }

    suspend fun addMyBeer(finishPrevious: Boolean = false): BeerEvent? {
        val d = defaultDrink()
        val chosen = d.copy(size = BeerRules.clamp(d.size))
        return write { insertBeer(finishPrevious, chosen, VersusRules::meSide) }
    }

    private suspend fun insertBeer(finishPrevious: Boolean, drink: DrinkChoice, pickSide: (Session?) -> Side?): BeerEvent? {
        val size = drink.size
        val now = clock()
        val active = activeSession(now)
        val side = pickSide(active) ?: return null
        if (!VersusRules.fits(side, active?.mode)) return null
        if (active?.mode == SessionMode.GROUP && seats.seats(active.id).none { it.side == side }) return null
        if (finishPrevious && active != null) beers.latestOnSide(active.id, side)?.let { beers.finish(it.id, now) }
        val session = active ?: Session(startedAt = now).let { it.copy(id = sessions.insert(it)) }
        val priced = price(drink.kind)?.takeIf { NoteRules.isValidAmount(it) && it > 0 }
        val event = BeerEvent(sessionId = session.id, side = side, sizeMl = size.sizeMl, abv = size.abv, at = now, kind = drink.kind, priceMinor = priced)
        return event.copy(id = beers.insert(event)).also { pending += Event.BeerAdded(it, VersusRules.isMine(it, session)) }
    }

    suspend fun startVersus(setup: VersusSetup): Session = write {
        val now = clock()
        activeSession(now)?.let { close(it) }
        val session = VersusRules.apply(Session(startedAt = now, mode = SessionMode.VERSUS), setup)
        session.copy(id = sessions.insert(session))
    }

    fun seats(sessionId: Long): Flow<List<GroupSeat>> = seats.observe(sessionId)

    suspend fun startGroup(setup: GroupSetup): Session = write {
        val now = clock()
        activeSession(now)?.let { close(it) }
        val session = Session(startedAt = now, mode = SessionMode.GROUP, meSide = GroupRules.me(setup))
        val id = sessions.insert(session)
        seats.insert(GroupRules.seats(id, setup))
        session.copy(id = id)
    }

    /** Rename seats, change "me" or add seats; seats are never removed mid-session (their drinks would be orphaned). */
    suspend fun updateGroup(setup: GroupSetup) = write {
        val session = activeSession(clock())?.takeIf { it.mode == SessionMode.GROUP } ?: return@write
        val current = seats.seats(session.id).size
        val names = setup.names + List((current - setup.names.size).coerceAtLeast(0)) { null }
        val fixed = setup.copy(names = names)
        seats.clear(session.id)
        seats.insert(GroupRules.seats(session.id, fixed))
        sessions.update(session.copy(meSide = GroupRules.me(fixed)))
    }

    suspend fun updateVersus(setup: VersusSetup) = write {
        val session = activeSession(clock())?.takeIf { it.mode == SessionMode.VERSUS } ?: return@write
        sessions.update(VersusRules.apply(session, setup))
    }

    suspend fun undo(event: BeerEvent) = write {
        if (beers.delete(event.id) > 0) deleteIfEmpty(event.sessionId)
    }

    suspend fun removeLatest(side: Side = Side.SOLO) = write {
        val session = activeSession(clock()) ?: return@write
        val latest = beers.latestOnSide(session.id, side) ?: return@write
        beers.delete(latest.id)
        deleteIfEmpty(session.id)
    }

    suspend fun endSession() = write {
        close(sessions.open() ?: return@write)
    }

    private suspend fun close(session: Session) {
        if (isEmpty(session.id)) sessions.delete(session.id) else sessions.end(session.id, clock())
        pending += Event.SessionClosed(session.id)
    }

    suspend fun addNote(text: String, amountMinor: Long?, side: Side? = null): Note? = write {
        val clean = NoteRules.cleanText(text) ?: return@write null
        val now = clock()
        val session = activeSession(now) ?: Session(startedAt = now).let { it.copy(id = sessions.insert(it)) }
        val note = Note(
            sessionId = session.id,
            text = clean,
            amountMinor = amountMinor?.takeIf { NoteRules.isValidAmount(it) },
            createdAt = now,
            side = VersusRules.noteSide(side, session.mode),
        )
        note.copy(id = notes.insert(note))
    }

    suspend fun updateNote(note: Note) = write {
        val clean = NoteRules.cleanText(note.text) ?: return@write
        val mode = sessions.open()?.takeIf { it.id == note.sessionId }?.mode ?: SessionMode.SOLO
        notes.update(
            note.copy(
                text = clean,
                side = VersusRules.noteSide(note.side, mode),
                person = NoteRules.cleanPerson(note.person),
                amountMinor = note.amountMinor?.takeIf { NoteRules.isValidAmount(it) },
            ),
        )
    }

    suspend fun updateBeer(event: BeerEvent) = write {
        val now = clock()
        val size = BeerRules.clamp(BeerSize(event.sizeMl, event.abv))
        val at = event.at.coerceAtMost(now)
        val finishedAt = event.finishedAt?.coerceIn(at, now)
        beers.update(event.id, at, finishedAt, size.sizeMl, size.abv)
    }

    suspend fun deleteNote(note: Note) = write {
        notes.delete(note.id)
        deleteIfEmpty(note.sessionId)
    }

    /** In a group, water goes to [side] (or my seat); elsewhere [side] is ignored. */
    suspend fun addWater(side: Side? = null): WaterEvent? = write {
        val session = activeSession(clock()) ?: return@write null
        val seat = if (session.mode == SessionMode.GROUP) {
            (side ?: session.meSide)?.takeIf { s -> seats.seats(session.id).any { it.side == s } } ?: return@write null
        } else {
            null
        }
        val event = WaterEvent(sessionId = session.id, at = clock(), side = seat)
        event.copy(id = water.insert(event))
    }

    /** Water −1 on Tonight: removes my latest glass (solo/Versus water has no side). */
    suspend fun removeLatestWater(side: Side? = null) = write {
        val session = activeSession(clock()) ?: return@write
        val latest = water.latest(session.id, side) ?: return@write
        water.delete(latest.id)
        deleteIfEmpty(session.id)
    }

    suspend fun undoWater(event: WaterEvent) = write {
        if (water.delete(event.id) > 0) deleteIfEmpty(event.sessionId)
    }

    suspend fun closeIfStale() = write { activeSession(clock()) }

    /** P27 storage upkeep: runs [block] under the write lock so it never races a +1. No events are sent. */
    suspend fun <T> exclusive(block: suspend () -> T): T = writeLock.withLock { block() }

    private suspend fun <T> write(block: suspend () -> T): T {
        val (result, events) = writeLock.withLock {
            val r = block()
            r to pending.toList().also { pending.clear() }
        }
        for (e in events) {
            when (e) {
                is Event.BeerAdded -> listener.onBeerAdded(e.event, e.mine)
                is Event.SessionClosed -> listener.onSessionClosed(e.sessionId)
            }
        }
        listener.onChanged()
        return result
    }

    private suspend fun activeSession(now: Long): Session? {
        val session = sessions.open() ?: return null
        val lastActivity = lastActivity(session)
        if (!SessionRules.isStale(lastActivity, now, inactivityMs)) return session
        sessions.end(session.id, lastActivity)
        pending += Event.SessionClosed(session.id)
        return null
    }

    private suspend fun lastActivity(session: Session): Long = maxOf(
        session.startedAt,
        beers.latest(session.id)?.at ?: 0,
        notes.lastCreatedAt(session.id) ?: 0,
        water.lastAt(session.id) ?: 0,
    )

    private suspend fun deleteIfEmpty(sessionId: Long) {
        if (!isEmpty(sessionId) || VersusRules.hasSides(sessions.open()?.takeIf { it.id == sessionId }?.mode)) return
        sessions.delete(sessionId)
        pending += Event.SessionClosed(sessionId)
    }

    private suspend fun isEmpty(sessionId: Long) =
        beers.count(sessionId) == 0 && notes.count(sessionId) == 0 && water.count(sessionId) == 0
}

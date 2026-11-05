package com.cansin.app.data

import com.cansin.app.domain.BeerSize
import com.cansin.app.domain.DrinkChoice
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
    private val notes = FakeNoteDao()
    private val water = FakeWaterDao()
    private var now = 1_000_000L
    private val repo = SessionRepository(sessions, beers, notes, water, clock = { now })

    private suspend fun openCount() = repo.openSession.first()?.let { beers.count(it.id) } ?: 0

    @Test
    fun firstTapStartsSessionAndLaterTapsReuseIt() = runBlocking {
        val first = repo.add()
        now += 60_000
        val second = repo.add()
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
        repo.add()
        repo.removeLatest()
        repo.removeLatest()
        assertNull(repo.openSession.first())
        assertEquals(0, sessions.rows.value.size)
        assertEquals(0, beers.rows.value.size)
    }

    @Test
    fun removeLatestRemovesNewestOnly() = runBlocking {
        repo.add()
        now += 1_000
        val newest = repo.add()
        repo.removeLatest()
        assertEquals(1, openCount())
        assertEquals(false, beers.rows.value.any { it.id == newest.id })
    }

    @Test
    fun undoIsIdempotent() = runBlocking {
        repo.add()
        val event = repo.add()
        repo.undo(event)
        repo.undo(event)
        assertEquals(1, openCount())
    }

    @Test
    fun undoOfOnlyBeerDeletesSession() = runBlocking {
        repo.undo(repo.add())
        assertEquals(0, sessions.rows.value.size)
    }

    @Test
    fun staleSessionClosesAtLastBeerAndNewTapStartsNewSession() = runBlocking {
        val old = repo.add()
        now += SessionRules.DEFAULT_INACTIVITY_MS
        val fresh = repo.add()
        assertNotEquals(old.sessionId, fresh.sessionId)
        assertEquals(old.at, sessions.rows.value.first { it.id == old.sessionId }.endedAt)
        assertEquals(1, openCount())
    }

    @Test
    fun closeIfStaleEndsInactiveSession() = runBlocking {
        val beer = repo.add()
        now += SessionRules.DEFAULT_INACTIVITY_MS - 1
        repo.closeIfStale()
        assertEquals(beer.sessionId, repo.openSession.first()?.id)
        now += 1
        repo.closeIfStale()
        assertNull(repo.openSession.first())
    }

    @Test
    fun removeLatestDoesNotTouchStaleSession() = runBlocking {
        repo.add()
        now += SessionRules.DEFAULT_INACTIVITY_MS
        repo.removeLatest()
        assertEquals(1, beers.rows.value.size)
    }

    @Test
    fun endSessionSetsEndTime() = runBlocking {
        val beer = repo.add()
        now += 5_000
        repo.endSession()
        assertEquals(now, sessions.rows.value.first { it.id == beer.sessionId }.endedAt)
        assertNull(repo.openSession.first())
    }

    @Test
    fun snapshotReflectsOpenSession() = runBlocking {
        assertEquals(CounterSnapshot(0, null), repo.snapshot())
        repo.add()
        now += 1_000
        val last = repo.add()
        val snapshot = repo.snapshot()
        assertEquals(2, snapshot.count)
        assertEquals(last.at, snapshot.lastBeerAt)
        assertEquals(last.sessionId, snapshot.sessionId)
        assertEquals(last.id, snapshot.myLatestBeer?.id)
    }

    @Test
    fun snapshotTreatsStaleSessionAsEmptyWithoutWriting() = runBlocking {
        repo.add()
        now += SessionRules.DEFAULT_INACTIVITY_MS
        assertEquals(CounterSnapshot(0, null), repo.snapshot())
        assertNull(sessions.rows.value.single().endedAt)
    }

    private class RecordingListener : SessionListener {
        val log = mutableListOf<String>()
        override suspend fun onChanged() { log += "changed" }
        override suspend fun onBeerAdded(event: BeerEvent, mine: Boolean) { log += "added:${event.sessionId}" }
        override suspend fun onSessionClosed(sessionId: Long) { log += "closed:$sessionId" }
    }

    private val listener = RecordingListener()
    private val observed = SessionRepository(sessions, beers, notes, water, clock = { now }, listener = listener)

    @Test
    fun everyWriteNotifiesChange() = runBlocking {
        val beer = observed.add()
        observed.undo(beer)
        observed.removeLatest()
        observed.endSession()
        assertEquals(4, listener.log.count { it == "changed" })
    }

    @Test
    fun addEmitsBeerAddedBeforeChange() = runBlocking {
        val beer = observed.add()
        assertEquals(listOf("added:${beer.sessionId}", "changed"), listener.log)
    }

    @Test
    fun undoingOnlyBeerEmitsSessionClosed() = runBlocking {
        val beer = observed.add()
        listener.log.clear()
        observed.undo(beer)
        assertEquals(listOf("closed:${beer.sessionId}", "changed"), listener.log)
    }

    @Test
    fun staleCloseIsEmittedBeforeNewSessionBeer() = runBlocking {
        val old = observed.add()
        now += SessionRules.DEFAULT_INACTIVITY_MS
        listener.log.clear()
        val fresh = observed.add()
        assertEquals(listOf("closed:${old.sessionId}", "added:${fresh.sessionId}", "changed"), listener.log)
    }

    @Test
    fun endSessionEmitsClosed() = runBlocking {
        val beer = observed.add()
        listener.log.clear()
        observed.endSession()
        assertEquals(listOf("closed:${beer.sessionId}", "changed"), listener.log)
    }

    @Test
    fun finishPreviousSetsFinishedAtOnlyOnce() = runBlocking {
        val first = repo.add()
        now += 60_000
        repo.add(finishPrevious = true)
        assertEquals(now, beers.rows.value.first { it.id == first.id }.finishedAt)
        now += 60_000
        repo.add()
        assertEquals(now - 60_000, beers.rows.value.first { it.id == first.id }.finishedAt)
    }

    @Test
    fun plainAddDoesNotFinishPrevious() = runBlocking {
        val first = repo.add()
        repo.add()
        assertNull(beers.rows.value.first { it.id == first.id }.finishedAt)
    }

    @Test
    fun concurrentTapsAreSerialized() = runBlocking(Dispatchers.Default) {
        (1..200).map { async { repo.add() } }.awaitAll()
        assertEquals(1, sessions.rows.value.size)
        assertEquals(200, openCount())
    }

    @Test
    fun beerUsesDefaultSizeUnlessOverridden() = runBlocking {
        val sized = SessionRepository(sessions, beers, notes, water, clock = { now }, defaultDrink = { DrinkChoice(DrinkKind.BEER, BeerSize(330, 4.5)) })
        assertEquals(BeerSize(330, 4.5), sized.add().let { BeerSize(it.sizeMl, it.abv) })
        assertEquals(BeerSize(568, 6.0), sized.add(size = BeerSize(568, 6.0)).let { BeerSize(it.sizeMl, it.abv) })
        assertEquals(listOf(330, 568), beers.rows.value.map { it.sizeMl })
    }

    @Test
    fun drinkKindIsStoredAndDefaultFollowsSetting() = runBlocking {
        val wine = DrinkChoice(DrinkKind.WINE, BeerSize(150, 12.0))
        val r = SessionRepository(sessions, beers, notes, water, clock = { now }, defaultDrink = { wine })
        assertEquals(DrinkKind.WINE, r.addBeer()!!.kind)
        assertEquals(DrinkKind.SHOT, r.addBeer(drink = DrinkChoice(DrinkKind.SHOT, BeerSize(40, 40.0)))!!.kind)
        assertEquals(DrinkKind.WINE, r.addBeer(size = BeerSize(200, 13.0))!!.kind)
        assertEquals(listOf(150, 40, 200), beers.rows.value.map { it.sizeMl })
    }

    @Test
    fun outOfRangeSizeIsClamped() = runBlocking {
        val event = repo.add(size = BeerSize(50_000, 99.0))
        assertEquals(2_000, event.sizeMl)
        assertEquals(80.0, event.abv, 0.0)
    }

    @Test
    fun updateBeerClampsTimesAndSize() = runBlocking {
        val beer = repo.add()
        repo.updateBeer(beer.copy(at = now + 60_000, finishedAt = beer.at - 60_000, sizeMl = 5, abv = 95.0))
        val stored = beers.rows.value.single()
        assertEquals(now, stored.at)
        assertEquals(now, stored.finishedAt)
        assertEquals(BeerSize(20, 80.0), BeerSize(stored.sizeMl, stored.abv))
    }

    @Test
    fun snapshotIncludesTabAndWater() = runBlocking {
        repo.add()
        repo.addNote("round", 1_250)
        repo.addNote("tip", null)
        repo.addWater()
        val snapshot = repo.snapshot()
        assertEquals(2, snapshot.notes)
        assertEquals(1_250L, snapshot.tabMinor)
        assertEquals(1, snapshot.water)
        assertEquals(true, snapshot.hasMe)
    }

    @Test
    fun finishBeerOnlyForOpenSession() = runBlocking {
        val beer = repo.add()
        repo.finishBeer(beer.id, beer.sessionId + 1)
        assertNull(beers.rows.value.single().finishedAt)
        now += 60_000
        repo.finishBeer(beer.id, beer.sessionId)
        assertEquals(now, beers.rows.value.single().finishedAt)
    }

    @Test
    fun priceIsSavedOnTheDrinkAndLeavesWithIt() = runBlocking {
        val prices = mapOf(DrinkKind.BEER to 15_000L)
        val priced = SessionRepository(sessions, beers, notes, water, clock = { now }, price = { prices[it] })
        val first = priced.addBeer()!!
        priced.addBeer()
        priced.addBeer(drink = DrinkChoice(DrinkKind.WINE, BeerSize(150, 12.0)))
        assertEquals(15_000L, first.priceMinor)
        assertEquals(listOf(15_000L, 15_000L, null), beers.rows.value.map { it.priceMinor })
        assertEquals(30_000L, priced.snapshot().tabMinor)
        priced.removeLatest() // wine (no price)
        priced.removeLatest()
        assertEquals(15_000L, priced.snapshot().tabMinor)
        priced.undo(first)
        assertEquals(0L, beers.rows.value.sumOf { it.priceMinor ?: 0 })
    }
}

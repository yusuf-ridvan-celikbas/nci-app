package com.cansin.app.data

import com.cansin.app.domain.SessionRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import com.cansin.app.domain.PriceRules
import com.cansin.app.domain.PriceSlot
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

    @Test
    fun waterMinusOneRemovesLatestGlassAndEmptySession() = runBlocking {
        val beer = repo.add()
        repo.addWater()
        now += 1_000
        val second = repo.addWater()!!
        repo.removeLatestWater()
        assertEquals(1, water.rows.value.size)
        assertEquals(false, water.rows.value.any { it.id == second.id })
        repo.removeLatestWater()
        repo.undo(beer)
        assertNull(repo.openSession.first())
        repo.removeLatestWater() // nothing open: no-op
    }

    @Test
    fun waterPriceAndSizesGoOnTheTab() = runBlocking {
        val prices = mapOf(PriceSlot.WATER to 2_000L)
        val priced = SessionRepository(sessions, beers, notes, water, clock = { now }, waterPrice = { PriceRules.water(prices, it) }, defaultWaterMl = { 500 })
        priced.add()
        priced.addWater()
        priced.addWater(sizeMl = 1_000)
        priced.addWater(sizeMl = 333) // not a size → default 500
        assertEquals(listOf(500, 1_000, 500), water.rows.value.map { it.sizeMl })
        assertEquals(listOf(2_000L, 4_000L, 2_000L), water.rows.value.map { it.priceMinor })
        priced.setWaterPrice(null, 500, 1_500)
        now += 1_000
        priced.addWater()
        assertEquals(1_500L, water.rows.value.last().priceMinor) // tonight's price sticks
        priced.removeLatestWater(null, 1_000)
        assertEquals(1_500L * 3, priced.snapshot().tabMinor)
    }
}

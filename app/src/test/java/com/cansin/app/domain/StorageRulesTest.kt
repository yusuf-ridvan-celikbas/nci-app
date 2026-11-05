package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.Note
import com.cansin.app.data.Session
import com.cansin.app.data.SessionMode
import com.cansin.app.data.SessionTotals
import com.cansin.app.data.Side
import com.cansin.app.data.WaterEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StorageRulesTest {
    private val min = 60_000L
    private val utc = ZoneOffset.UTC
    private val profile = BodyProfile(Sex.MALE, 30, 180, 80, License.NONE)

    private fun millis(date: String, hour: Int = 0) = LocalDate.parse(date).atTime(hour, 0).toInstant(utc).toEpochMilli()

    @Test
    fun cutoffIsStartOfDayCalendarMonthsBack() {
        val now = millis("2026-11-06", 15)
        assertEquals(millis("2026-08-06"), StorageRules.cutoff(now, 3, utc))
        assertEquals(millis("2025-11-06"), StorageRules.cutoff(now, 12, utc))
        assertNull(StorageRules.cutoff(now, null, utc))
        // Month end: 31 May − 3 months = 28 Feb.
        assertEquals(millis("2026-02-28"), StorageRules.cutoff(millis("2026-05-31", 9), 3, utc))
        assertNull(StorageRules.cutoff(now, KeepHistory.FOREVER.months, utc))
        assertNull(StorageRules.cutoff(now, CompressAfter.NEVER.months, utc))
    }

    @Test
    fun dueOncePerDayOrAfterClockGoesBack() {
        val now = 10 * StorageRules.DAY_MS
        assertTrue(StorageRules.isDue(null, now))
        assertFalse(StorageRules.isDue(now - 1_000, now))
        assertTrue(StorageRules.isDue(now - StorageRules.DAY_MS, now))
        assertTrue(StorageRules.isDue(now + 1_000, now))
    }

    @Test
    fun soloTotalsMatchLiveNumbers() {
        val s = Session(id = 1, startedAt = 0, endedAt = 120 * min)
        val beers = listOf(0L, 30, 60).map { BeerEvent(sessionId = 1, at = it * min) }
        val water = listOf(WaterEvent(sessionId = 1, at = 10 * min), WaterEvent(sessionId = 1, at = 40 * min))
        val notes = listOf(Note(sessionId = 1, text = "a", amountMinor = 1250, createdAt = 1), Note(sessionId = 1, text = "b", createdAt = 2))
        val t = StorageRules.summarize(s, beers, water, notes, profile, now = 99)
        val live = StatsRules.session(s, beers, profile)!!
        assertEquals(SessionTotals(99, 3, 3, 2, 2, 2, 1250, live.paceMin, live.peak, null), t)
        // Stats read the stored totals once the rows are gone.
        assertEquals(live, StatsRules.session(s.copy(totals = t), emptyList(), profile))
    }

    @Test
    fun versusAndGroupKeepPerSideCounts() {
        val v = Session(id = 2, startedAt = 0, endedAt = 60 * min, mode = SessionMode.VERSUS, meSide = Side.RED)
        val vb = listOf(BeerEvent(sessionId = 2, side = Side.RED, at = 1), BeerEvent(sessionId = 2, side = Side.BLUE, at = 2), BeerEvent(sessionId = 2, side = Side.RED, at = 3))
        val vt = StorageRules.summarize(v, vb, listOf(WaterEvent(sessionId = 2, at = 4)), emptyList(), null, 0)
        assertEquals(2, vt.mine)
        assertEquals(1, vt.myWater)
        assertEquals("RED:2:0,BLUE:1:0", vt.sides)

        val g = Session(id = 3, startedAt = 0, endedAt = 60 * min, mode = SessionMode.GROUP, meSide = null)
        val gb = listOf(BeerEvent(sessionId = 3, side = Side.TEAL, at = 1), BeerEvent(sessionId = 3, side = Side.RED, at = 2))
        val gw = listOf(WaterEvent(sessionId = 3, at = 3, side = Side.TEAL))
        val gt = StorageRules.summarize(g, gb, gw, emptyList(), null, 0)
        assertNull(gt.mine)
        assertNull(gt.myWater)
        assertEquals(listOf(SideCount(Side.RED, 1, 0), SideCount(Side.TEAL, 1, 1)), StorageRules.decodeSides(gt.sides))
        // No "me" → no stats, same as before compression.
        assertNull(StatsRules.session(g.copy(totals = gt), emptyList(), null))
    }

    @Test
    fun sidesCodecIsStrict() {
        val counts = listOf(SideCount(Side.RED, 3, 1), SideCount(Side.BLUE, 0, 0))
        assertEquals(counts, StorageRules.decodeSides(StorageRules.encodeSides(counts)))
        assertNull(StorageRules.encodeSides(emptyList()))
        assertEquals(emptyList<SideCount>(), StorageRules.decodeSides(null))
        assertEquals(listOf(SideCount(Side.RED, 1, 0)), StorageRules.decodeSides("RED:1:0,PINKISH:1:0,SOLO:2:0,RED:5:5,BLUE:-1:0,GREEN:1:x,GREY:1"))
        assertEquals(emptyList<SideCount>(), StorageRules.decodeSides("RED:1:0," + "x".repeat(500)))
    }

    @Test
    fun validRejectsOutOfRangeTotals() {
        val ok = SessionTotals(1, 3, 2, 1, 1, 0, 0, 12.5, 0.4, "RED:3:1")
        assertTrue(StorageRules.valid(ok))
        assertFalse(StorageRules.valid(ok.copy(drinks = -1)))
        assertFalse(StorageRules.valid(ok.copy(mine = 4)))
        assertFalse(StorageRules.valid(ok.copy(myWater = 2)))
        assertFalse(StorageRules.valid(ok.copy(peak = 9.0)))
        assertFalse(StorageRules.valid(ok.copy(spentMinor = -5)))
        assertFalse(StorageRules.valid(ok.copy(sides = "nope")))
    }
}

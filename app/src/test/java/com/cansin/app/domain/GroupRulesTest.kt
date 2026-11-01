package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.GroupSeat
import com.cansin.app.data.Note
import com.cansin.app.data.Session
import com.cansin.app.data.SessionMode
import com.cansin.app.data.Side
import com.cansin.app.data.WaterEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRulesTest {
    private val seats = GroupRules.seats(7, GroupSetup(listOf(" Ali ", null, "Can")))

    @Test
    fun seatsAreCleanedAndClamped() {
        assertEquals(listOf(Side.RED, Side.BLUE, Side.GREEN), seats.map { it.side })
        assertEquals(listOf("Ali", null, "Can"), seats.map { it.name })
        assertEquals(2, GroupRules.seats(1, GroupSetup(listOf("x"))).size)
        assertEquals(6, GroupRules.seats(1, GroupSetup(List(9) { "p$it" })).size)
    }

    @Test
    fun meMustBeAtTheTable() {
        assertEquals(Side.BLUE, GroupRules.me(GroupSetup(listOf("a", "b"), Side.BLUE)))
        assertNull(GroupRules.me(GroupSetup(listOf("a", "b"), Side.GREEN)))
        assertNull(GroupRules.me(GroupSetup(listOf("a", "b"), Side.SOLO)))
    }

    @Test
    fun tallyIsInSeatOrderNotByCount() {
        val beers = listOf(Side.GREEN, Side.GREEN, Side.GREEN, Side.RED).map { BeerEvent(sessionId = 7, side = it, at = 0) }
        val water = listOf(WaterEvent(sessionId = 7, at = 0, side = Side.BLUE))
        val t = GroupRules.tally(seats.reversed(), beers, water)
        assertEquals(listOf(Side.RED, Side.BLUE, Side.GREEN), t.map { it.side })
        assertEquals(listOf(1, 0, 3), t.map { it.drinks })
        assertEquals(listOf(0, 1, 0), t.map { it.water })
    }

    @Test
    fun sharedSplitsEvenlyAndSumsExactly() {
        val notes = listOf(
            Note(sessionId = 7, text = "a", amountMinor = 1_000, createdAt = 0, side = Side.RED),
            Note(sessionId = 7, text = "b", amountMinor = 100, createdAt = 0),
            Note(sessionId = 7, text = "c", amountMinor = 50, createdAt = 0, side = Side.PURPLE),
        )
        val (own, shared) = GroupRules.tab(seats, notes)
        assertEquals(1_000L, own[Side.RED])
        assertEquals(150L, shared)
        val pays = GroupRules.split(seats, notes, splitShared = true)
        assertEquals(listOf(1_050L, 50L, 50L), pays.map { it.pays })
        assertEquals(1_150L, pays.sumOf { it.pays })
        val odd = GroupRules.split(seats, listOf(Note(sessionId = 7, text = "x", amountMinor = 100, createdAt = 0)), true)
        assertEquals(listOf(34L, 33L, 33L), odd.map { it.pays })
        assertEquals(listOf(1_000L, 0L, 0L), GroupRules.split(seats, notes, splitShared = false).map { it.pays })
    }

    @Test
    fun sideRulesPerMode() {
        assertTrue(VersusRules.fits(Side.GREEN, SessionMode.GROUP))
        assertFalse(VersusRules.fits(Side.SOLO, SessionMode.GROUP))
        assertFalse(VersusRules.fits(Side.GREEN, SessionMode.VERSUS))
        assertTrue(VersusRules.fits(Side.BLUE, SessionMode.VERSUS))
        assertTrue(VersusRules.fits(Side.SOLO, SessionMode.SOLO))
        assertEquals(Side.YELLOW, VersusRules.noteSide(Side.YELLOW, SessionMode.GROUP))
        assertNull(VersusRules.noteSide(Side.YELLOW, SessionMode.VERSUS))
        assertNull(VersusRules.noteSide(Side.RED, SessionMode.SOLO))
    }

    @Test
    fun onlyMySeatsWaterIsMine() {
        val group = Session(id = 7, startedAt = 0, mode = SessionMode.GROUP, meSide = Side.BLUE)
        assertTrue(VersusRules.isMineWater(WaterEvent(sessionId = 7, at = 0, side = Side.BLUE), group))
        assertFalse(VersusRules.isMineWater(WaterEvent(sessionId = 7, at = 0, side = Side.RED), group))
        assertFalse(VersusRules.isMineWater(WaterEvent(sessionId = 7, at = 0, side = Side.BLUE), group.copy(meSide = null)))
        assertTrue(VersusRules.isMineWater(WaterEvent(sessionId = 1, at = 0), Session(id = 1, startedAt = 0)))
    }

    @Test
    fun orderedFollowsSides() {
        val mixed = listOf(GroupSeat(1, Side.AMBER), GroupSeat(1, Side.RED), GroupSeat(1, Side.PURPLE))
        assertEquals(listOf(Side.RED, Side.PURPLE, Side.AMBER), GroupRules.ordered(mixed).map { it.side })
    }
}

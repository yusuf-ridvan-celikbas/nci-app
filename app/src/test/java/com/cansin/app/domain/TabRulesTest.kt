package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.DrinkKind
import com.cansin.app.data.GroupSeat
import com.cansin.app.data.Note
import com.cansin.app.data.Session
import com.cansin.app.data.SessionMode
import com.cansin.app.data.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabRulesTest {
    private fun beer(id: Long, side: Side = Side.SOLO, price: Long? = 15_000, kind: DrinkKind = DrinkKind.BEER, size: Int = 500) =
        BeerEvent(id = id, sessionId = 1, side = side, sizeMl = size, at = id * 1_000, kind = kind, priceMinor = price)

    @Test
    fun unpricedDrinksStayOffTheTab() {
        val beers = listOf(beer(1), beer(2, price = null), beer(3, price = 0))
        assertEquals(1, TabRules.charges(beers, SessionMode.SOLO).size)
        assertEquals(15_000L, TabRules.drinkTotal(beers))
        assertTrue(TabRules.drinkLines(listOf(beer(1, price = null)), SessionMode.SOLO).isEmpty())
    }

    @Test
    fun linesGroupByKindSizeAndSideInOrder() {
        val beers = listOf(
            beer(1), beer(2, kind = DrinkKind.RAKI, size = 50, price = 22_000), beer(3), beer(4, size = 330, price = 10_000),
        )
        assertEquals(
            listOf(
                DrinkLine(DrinkKind.BEER, 500, null, 2, 30_000),
                DrinkLine(DrinkKind.RAKI, 50, null, 1, 22_000),
                DrinkLine(DrinkKind.BEER, 330, null, 1, 10_000),
            ),
            TabRules.drinkLines(beers, SessionMode.SOLO),
        )
    }

    @Test
    fun versusDrinksLandOnTheirSide() {
        val beers = listOf(beer(1, Side.RED), beer(2, Side.BLUE), beer(3, Side.BLUE))
        val notes = listOf(Note(sessionId = 1, text = "nuts", amountMinor = 8_000, createdAt = 0))
        val totals = VersusRules.totals(TabRules.withDrinks(notes, beers, SessionMode.VERSUS))
        assertEquals(15_000L, totals.red)
        assertEquals(30_000L, totals.blue)
        assertEquals(8_000L, totals.shared)
        assertEquals(listOf(Side.RED, Side.BLUE), TabRules.drinkLines(beers, SessionMode.VERSUS).map { it.side })
        // Budget: my side + shared.
        val me = Session(id = 1, startedAt = 0, mode = SessionMode.VERSUS, meSide = Side.BLUE)
        assertEquals(38_000L, CareRules.mySpend(me, TabRules.withDrinks(notes, beers, SessionMode.VERSUS)))
    }

    @Test
    fun groupDrinksGoToSeats() {
        val seats = listOf(GroupSeat(1, Side.RED, "Ali"), GroupSeat(1, Side.TEAL, "Ece"))
        val beers = listOf(beer(1, Side.TEAL), beer(2, Side.TEAL), beer(3, Side.RED, price = null))
        val (own, shared) = GroupRules.tab(seats, TabRules.withDrinks(emptyList(), beers, SessionMode.GROUP))
        assertEquals(30_000L, own[Side.TEAL])
        assertEquals(0L, own[Side.RED])
        assertEquals(0L, shared)
    }

    @Test
    fun soloChargesHaveNoSideAndCountForBudget() {
        val charges = TabRules.charges(listOf(beer(1), beer(2)), SessionMode.SOLO)
        assertNull(charges.first().side)
        assertTrue(charges.all { it.id < 0 })
        assertEquals(30_000L, CareRules.mySpend(Session(id = 1, startedAt = 0), charges))
    }

    @Test
    fun compressedNightKeepsDrinkPricesInTheTab() {
        val s = Session(id = 1, startedAt = 0, endedAt = 60_000)
        val notes = listOf(Note(sessionId = 1, text = "nuts", amountMinor = 8_000, createdAt = 0))
        val t = StorageRules.summarize(s, listOf(beer(1), beer(2)), emptyList(), notes, null, 0)
        assertEquals(38_000L, t.spentMinor)
    }
}

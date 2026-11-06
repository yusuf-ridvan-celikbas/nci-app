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
import com.cansin.app.data.WaterEvent
import org.junit.Test

class TabRulesTest {
    private fun beer(id: Long, side: Side = Side.SOLO, price: Long? = 15_000, kind: DrinkKind = DrinkKind.BEER, size: Int = 500) =
        BeerEvent(id = id, sessionId = 1, side = side, sizeMl = size, at = id * 1_000, kind = kind, priceMinor = price)

    @Test
    fun unpricedDrinksStayOffTheTab() {
        val beers = listOf(beer(1), beer(2, price = null), beer(3, price = 0))
        assertEquals(1, TabRules.charges(beers, SessionMode.SOLO).size)
        assertEquals(15_000L, TabRules.drinkTotal(beers))
        // Unpriced drinks are still counted on the Tab (count section), with no price.
        val line = TabRules.drinkLines(listOf(beer(1, price = null)), SessionMode.SOLO).single()
        assertEquals(1, line.count)
        assertNull(line.unitMinor)
        assertEquals(0L, line.totalMinor)
    }

    @Test
    fun linesGroupByKindSizeAndSideInOrder() {
        val beers = listOf(
            beer(1), beer(2, kind = DrinkKind.RAKI, size = 50, price = 22_000), beer(3), beer(4, size = 330, price = 10_000),
        )
        assertEquals(
            listOf(
                Triple(DrinkKind.BEER, 500, 2) to (30_000L to 15_000L),
                Triple(DrinkKind.RAKI, 50, 1) to (22_000L to 22_000L),
                Triple(DrinkKind.BEER, 330, 1) to (10_000L to 10_000L),
            ),
            TabRules.drinkLines(beers, SessionMode.SOLO).map { Triple(it.kind, it.sizeMl, it.count) to (it.totalMinor to it.unitMinor) },
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

    @Test
    fun waterLinesAndCharges() {
        val water = listOf(
            WaterEvent(id = 1, sessionId = 1, at = 1, priceMinor = 2_000),
            WaterEvent(id = 2, sessionId = 1, at = 2, priceMinor = 4_000, sizeMl = 1_000),
            WaterEvent(id = 3, sessionId = 1, at = 3, priceMinor = 2_000),
            WaterEvent(id = 4, sessionId = 1, at = 4),
        )
        val lines = TabRules.waterLines(water)
        assertEquals(listOf(500 to 3, 1_000 to 1), lines.map { it.sizeMl to it.count })
        assertEquals(4_000L, lines[0].totalMinor)
        assertEquals(8_000L, TabRules.waterTotal(water))
        val all = TabRules.withDrinksAndWater(emptyList(), listOf(beer(1)), water, SessionMode.SOLO)
        assertEquals(23_000L, all.sumOf { it.amountMinor ?: 0 })
        assertEquals(all.size, all.map { it.id }.toSet().size) // ids never clash
    }

    @Test
    fun draftAndBottleGetTheirOwnLines() {
        val beers = listOf(beer(1).copy(serving = com.cansin.app.data.Serving.DRAFT), beer(2).copy(serving = com.cansin.app.data.Serving.BOTTLE), beer(3).copy(serving = com.cansin.app.data.Serving.DRAFT))
        assertEquals(listOf(com.cansin.app.data.Serving.DRAFT to 2, com.cansin.app.data.Serving.BOTTLE to 1), TabRules.drinkLines(beers, SessionMode.SOLO).map { it.serving to it.count })
    }
}

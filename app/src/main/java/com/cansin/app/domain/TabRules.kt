package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.DrinkKind
import com.cansin.app.data.Note
import com.cansin.app.data.SessionMode
import com.cansin.app.data.Side
import com.cansin.app.data.WaterEvent

/**
 * One Tab line: the same drink, size and person added [count] times tonight. [beerSide] is the drink's own side
 * (SOLO in solo); [side] is the Tab side (null when the Tab has no sides). [unitMinor] = latest price (null = no price),
 * [totalMinor] = sum of the priced ones. [abv] of the latest one, for +1.
 */
data class DrinkLine(
    val kind: DrinkKind,
    val sizeMl: Int,
    val side: Side?,
    val count: Int,
    val totalMinor: Long,
    val beerSide: Side = Side.SOLO,
    val unitMinor: Long? = null,
    val abv: Double = 5.0,
    val brand: String? = null,
)

/** P33 Tab water line for one seat (null side = solo/Versus). */
data class WaterLine(val side: Side?, val count: Int, val totalMinor: Long, val unitMinor: Long?, val sizeMl: Int = 500)

/**
 * P28: drinks with a price go on the Tab by themselves. The price is saved on the drink when it's added,
 * so undo / −1 take it off again and later price changes don't touch earlier drinks.
 * [charges] turns priced drinks into note-like items so the existing Tab math (Versus split, Together seats,
 * budget, settle up) works unchanged.
 */
object TabRules {
    fun charges(beers: List<BeerEvent>, mode: SessionMode?): List<Note> =
        beers.filter { (it.priceMinor ?: 0) > 0 }.map { b ->
            Note(
                id = -b.id,
                sessionId = b.sessionId,
                text = "",
                amountMinor = b.priceMinor,
                createdAt = b.at,
                side = mode?.let { VersusRules.noteSide(b.side, it) },
            )
        }

    fun withDrinks(notes: List<Note>, beers: List<BeerEvent>, mode: SessionMode?): List<Note> = notes + charges(beers, mode)

    /** P33: priced glasses of water as note-like items (seat side in Together, shared otherwise). */
    fun waterCharges(water: List<WaterEvent>, mode: SessionMode?): List<Note> =
        water.filter { (it.priceMinor ?: 0) > 0 }.map { w ->
            Note(id = -1_000_000_000L - w.id, sessionId = w.sessionId, text = "", amountMinor = w.priceMinor, createdAt = w.at,
                side = w.side?.let { s -> mode?.let { VersusRules.noteSide(s, it) } })
        }

    fun withDrinksAndWater(notes: List<Note>, beers: List<BeerEvent>, water: List<WaterEvent>, mode: SessionMode?): List<Note> =
        withDrinks(notes, beers, mode) + waterCharges(water, mode)

    fun waterTotal(water: List<WaterEvent>): Long = water.sumOf { it.priceMinor ?: 0 }

    /** Water on the Tab, one line per seat and glass size, in first-added order. */
    fun waterLines(water: List<WaterEvent>): List<WaterLine> =
        water.sortedWith(compareBy({ it.at }, { it.id })).groupBy { it.side to it.sizeMl }.map { (key, glasses) ->
            WaterLine(key.first, glasses.size, waterTotal(glasses), glasses.lastOrNull { (it.priceMinor ?: 0) > 0 }?.priceMinor, key.second)
        }

    fun drinkTotal(beers: List<BeerEvent>): Long = beers.sumOf { it.priceMinor ?: 0 }

    /** Every drink tonight grouped by kind + size + person + brand (priced or not), in first-added order. */
    fun drinkLines(beers: List<BeerEvent>, mode: SessionMode?): List<DrinkLine> {
        val lines = LinkedHashMap<List<Any?>, DrinkLine>()
        beers.sortedWith(compareBy({ it.at }, { it.id })).forEach { b ->
            val key = listOf(b.kind, b.sizeMl, b.side, b.brand)
            val price = b.priceMinor?.takeIf { it > 0 }
            val line = lines[key]
            lines[key] = if (line == null) {
                DrinkLine(b.kind, b.sizeMl, mode?.let { VersusRules.noteSide(b.side, it) }, 1, price ?: 0, b.side, price, b.abv, b.brand)
            } else {
                line.copy(count = line.count + 1, totalMinor = line.totalMinor + (price ?: 0), unitMinor = price ?: line.unitMinor, abv = b.abv)
            }
        }
        return lines.values.toList()
    }
}

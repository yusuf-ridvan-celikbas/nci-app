package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.DrinkKind
import com.cansin.app.data.Note
import com.cansin.app.data.SessionMode
import com.cansin.app.data.Side

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

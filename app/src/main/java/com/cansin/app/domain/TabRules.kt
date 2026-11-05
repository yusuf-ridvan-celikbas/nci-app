package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.DrinkKind
import com.cansin.app.data.Note
import com.cansin.app.data.SessionMode
import com.cansin.app.data.Side

/** One automatic Tab line: same drink, size and side added [count] times. [side] is null when the Tab has no sides. */
data class DrinkLine(val kind: DrinkKind, val sizeMl: Int, val side: Side?, val count: Int, val totalMinor: Long)

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

    fun drinkLines(beers: List<BeerEvent>, mode: SessionMode?): List<DrinkLine> {
        val lines = LinkedHashMap<Triple<DrinkKind, Int, Side?>, DrinkLine>()
        beers.filter { (it.priceMinor ?: 0) > 0 }.sortedWith(compareBy({ it.at }, { it.id })).forEach { b ->
            val side = mode?.let { VersusRules.noteSide(b.side, it) }
            val key = Triple(b.kind, b.sizeMl, side)
            val line = lines[key]
            lines[key] = if (line == null) {
                DrinkLine(b.kind, b.sizeMl, side, 1, b.priceMinor ?: 0)
            } else {
                line.copy(count = line.count + 1, totalMinor = line.totalMinor + (b.priceMinor ?: 0))
            }
        }
        return lines.values.toList()
    }
}

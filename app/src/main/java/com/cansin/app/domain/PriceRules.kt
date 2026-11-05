package com.cansin.app.domain

import com.cansin.app.data.DrinkKind
import kotlin.math.roundToLong

/** A price setting: beer has three sizes (33 cl, 50 cl, 1 L "Maß"); other kinds one price. Names are storage/backup keys. */
enum class PriceSlot(val kind: DrinkKind, val sizeMl: Int?) {
    BEER_33(DrinkKind.BEER, 330),
    BEER(DrinkKind.BEER, 500),
    BEER_1L(DrinkKind.BEER, 1_000),
    WINE(DrinkKind.WINE, null),
    RAKI(DrinkKind.RAKI, null),
    SHOT(DrinkKind.SHOT, null),
    COCKTAIL(DrinkKind.COCKTAIL, null),
}

object PriceRules {
    private const val WHOLE_UNIT = 100L

    /**
     * Price for a new drink. Beer: the exact size's price, else the 50 cl price (or another beer size) scaled by size,
     * rounded to a whole unit. Other kinds: their one price. Null = not on the Tab.
     */
    fun lookup(prices: Map<PriceSlot, Long>, kind: DrinkKind, sizeMl: Int): Long? {
        if (kind != DrinkKind.BEER) return PriceSlot.entries.firstOrNull { it.kind == kind }?.let { prices[it] }
        val beerSlots = PriceSlot.entries.filter { it.kind == DrinkKind.BEER }
        beerSlots.firstOrNull { it.sizeMl == sizeMl }?.let { slot -> prices[slot]?.let { return it } }
        val base = (listOf(PriceSlot.BEER) + beerSlots).firstOrNull { prices[it] != null } ?: return null
        val scaled = prices.getValue(base) * sizeMl / base.sizeMl!!.toDouble()
        return ((scaled / WHOLE_UNIT).roundToLong() * WHOLE_UNIT).coerceAtLeast(WHOLE_UNIT).takeIf { NoteRules.isValidAmount(it) }
    }
}

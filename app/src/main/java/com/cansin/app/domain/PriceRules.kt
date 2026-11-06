package com.cansin.app.domain

import com.cansin.app.data.DrinkKind
import kotlin.math.roundToLong

/** A price setting: beer has three sizes (33 cl, 50 cl, 1 L "Maß"); other kinds one price. Names are storage/backup keys. */
enum class PriceSlot(val kind: DrinkKind?, val sizeMl: Int?) {
    BEER_33(DrinkKind.BEER, 330),
    BEER(DrinkKind.BEER, 500),
    BEER_1L(DrinkKind.BEER, 1_000),
    WINE(DrinkKind.WINE, null),
    RAKI(DrinkKind.RAKI, null),
    SHOT(DrinkKind.SHOT, null),
    COCKTAIL(DrinkKind.COCKTAIL, null),
    /** P33: water (kind = null), per glass size; WATER = 500 ml (key kept). */
    WATER_250(null, 250),
    WATER(null, 500),
    WATER_1L(null, 1_000),
}

object PriceRules {
    private const val WHOLE_UNIT = 100L

    /**
     * Price for a new drink. Beer: the exact size's price, else the 50 cl price (or another beer size) scaled by size,
     * rounded to a whole unit. Other kinds: their one price. Null = not on the Tab.
     */
    /** Settings show one water price (500 ml); 250 ml / 1 L are worked out from it. */
    val VISIBLE = PriceSlot.entries - PriceSlot.WATER_250 - PriceSlot.WATER_1L

    /** P33 water price for a glass: the one water price (500 ml) scaled by size, whole units. */
    fun water(prices: Map<PriceSlot, Long>, sizeMl: Int): Long? = sized(prices, listOf(PriceSlot.WATER), PriceSlot.WATER, sizeMl)

    private fun sized(prices: Map<PriceSlot, Long>, slots: List<PriceSlot>, main: PriceSlot, sizeMl: Int): Long? {
        slots.firstOrNull { it.sizeMl == sizeMl }?.let { slot -> prices[slot]?.let { return it } }
        val base = (listOf(main) + slots).firstOrNull { prices[it] != null } ?: return null
        val scaled = prices.getValue(base) * sizeMl / base.sizeMl!!.toDouble()
        return ((scaled / WHOLE_UNIT).roundToLong() * WHOLE_UNIT).coerceAtLeast(WHOLE_UNIT).takeIf { NoteRules.isValidAmount(it) }
    }

    fun lookup(prices: Map<PriceSlot, Long>, kind: DrinkKind, sizeMl: Int): Long? {
        if (kind != DrinkKind.BEER) return PriceSlot.entries.firstOrNull { it.kind == kind }?.let { prices[it] }
        val beerSlots = PriceSlot.entries.filter { it.kind == DrinkKind.BEER }
        beerSlots.firstOrNull { it.sizeMl == sizeMl }?.let { slot -> prices[slot]?.let { return it } }
        val base = (listOf(PriceSlot.BEER) + beerSlots).firstOrNull { prices[it] != null } ?: return null
        val scaled = prices.getValue(base) * sizeMl / base.sizeMl!!.toDouble()
        return ((scaled / WHOLE_UNIT).roundToLong() * WHOLE_UNIT).coerceAtLeast(WHOLE_UNIT).takeIf { NoteRules.isValidAmount(it) }
    }
}

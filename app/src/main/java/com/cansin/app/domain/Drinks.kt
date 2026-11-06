package com.cansin.app.domain

import com.cansin.app.data.DrinkKind

/** What one +1 adds: the kind (for labels) and the real amount (for the ‰ estimate). */
data class DrinkChoice(val kind: DrinkKind, val size: BeerSize, val serving: com.cansin.app.data.Serving? = null) {
    companion object {
        val DEFAULT = DrinkChoice(DrinkKind.BEER, BeerSize.DEFAULT)
    }
}

object Drinks {
    /** Kinds that can be the default for +1; CUSTOM is only for a one-off long-press. */
    val DEFAULT_KINDS = DrinkKind.entries - DrinkKind.CUSTOM

    /** Typical serving; beer uses the user's own default beer size. Rakı = one "tek" (5 cl). */
    fun preset(kind: DrinkKind, beer: BeerSize): BeerSize = when (kind) {
        DrinkKind.BEER, DrinkKind.CUSTOM -> beer
        DrinkKind.WINE -> BeerSize(150, 12.0)
        DrinkKind.RAKI -> BeerSize(50, 45.0)
        DrinkKind.SHOT -> BeerSize(40, 40.0)
        DrinkKind.COCKTAIL -> BeerSize(200, 10.0)
    }

    /** Default +1 with draft/bottle (beer only). */
    fun default(kind: DrinkKind, beer: BeerSize, serving: com.cansin.app.data.Serving?): DrinkChoice =
        default(kind, beer).let { d -> if (d.kind == DrinkKind.BEER) d.copy(serving = serving) else d }

    fun default(kind: DrinkKind, beer: BeerSize): DrinkChoice {
        val k = kind.takeIf { it in DEFAULT_KINDS } ?: DrinkKind.BEER
        return DrinkChoice(k, BeerRules.clamp(preset(k, beer)))
    }

    /** Count per kind in a fixed order, only kinds that occur. */
    fun breakdown(kinds: List<DrinkKind>): List<Pair<DrinkKind, Int>> {
        val counts = kinds.groupingBy { it }.eachCount()
        return DrinkKind.entries.mapNotNull { k -> counts[k]?.let { k to it } }
    }

    /** Show the breakdown only when it says more than the total (not all beer). */
    fun showBreakdown(kinds: List<DrinkKind>): Boolean = kinds.any { it != DrinkKind.BEER }
}

package com.cansin.app.domain

import com.cansin.app.data.DrinkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceRulesTest {
    @Test
    fun beerUsesExactSizeElseScalesFromFiftyCl() {
        val prices = mapOf(PriceSlot.BEER to 15_000L, PriceSlot.BEER_1L to 28_000L)
        assertEquals(15_000L, PriceRules.lookup(prices, DrinkKind.BEER, 500))
        assertEquals(28_000L, PriceRules.lookup(prices, DrinkKind.BEER, 1_000))
        assertEquals(9_900L, PriceRules.lookup(prices, DrinkKind.BEER, 330)) // 150 × 33/50 = 99.00
        assertEquals(12_000L, PriceRules.lookup(prices, DrinkKind.BEER, 400))
        // Only a 33 cl price: other sizes scale from it.
        assertEquals(30_300L, PriceRules.lookup(mapOf(PriceSlot.BEER_33 to 10_000L), DrinkKind.BEER, 1_000)) // 100 × 100/33 ≈ 303
        assertNull(PriceRules.lookup(emptyMap(), DrinkKind.BEER, 500))
    }

    @Test
    fun otherKindsHaveOnePrice() {
        val prices = mapOf(PriceSlot.RAKI to 22_000L)
        assertEquals(22_000L, PriceRules.lookup(prices, DrinkKind.RAKI, 70))
        assertNull(PriceRules.lookup(prices, DrinkKind.WINE, 150))
        assertNull(PriceRules.lookup(prices, DrinkKind.CUSTOM, 100))
    }

    @Test
    fun waterPerSize() {
        val prices = mapOf(PriceSlot.WATER to 2_000L)
        assertEquals(2_000L, PriceRules.water(prices, 500))
        assertEquals(1_000L, PriceRules.water(prices, 250))
        assertEquals(4_000L, PriceRules.water(prices, 1_000))
        assertEquals(3_500L, PriceRules.water(prices + (PriceSlot.WATER_1L to 3_500L), 1_000))
        assertNull(PriceRules.water(emptyMap(), 500))
        // Beer lookup ignores water slots.
        assertNull(PriceRules.lookup(prices, DrinkKind.BEER, 500))
    }
}

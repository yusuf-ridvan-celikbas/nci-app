package com.cansin.app.domain

import com.cansin.app.data.DrinkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrinksTest {
    private val beer = BeerSize(330, 4.5)

    @Test
    fun beerUsesOwnSizeOthersUsePresets() {
        assertEquals(DrinkChoice(DrinkKind.BEER, beer), Drinks.default(DrinkKind.BEER, beer))
        assertEquals(DrinkChoice(DrinkKind.WINE, BeerSize(150, 12.0)), Drinks.default(DrinkKind.WINE, beer))
        assertEquals(BeerSize(40, 40.0), Drinks.preset(DrinkKind.SHOT, beer))
    }

    @Test
    fun customIsNeverTheDefault() {
        assertEquals(DrinkKind.BEER, Drinks.default(DrinkKind.CUSTOM, beer).kind)
        assertFalse(DrinkKind.CUSTOM in Drinks.DEFAULT_KINDS)
    }

    @Test
    fun presetsFitTheLimits() {
        DrinkKind.entries.forEach { assertEquals(it.name, Drinks.preset(it, beer), BeerRules.clamp(Drinks.preset(it, beer))) }
    }

    @Test
    fun breakdownInFixedOrderOnlyWhenMixed() {
        val kinds = listOf(DrinkKind.SHOT, DrinkKind.BEER, DrinkKind.BEER, DrinkKind.WINE)
        assertEquals(listOf(DrinkKind.BEER to 2, DrinkKind.WINE to 1, DrinkKind.SHOT to 1), Drinks.breakdown(kinds))
        assertTrue(Drinks.showBreakdown(kinds))
        assertFalse(Drinks.showBreakdown(listOf(DrinkKind.BEER, DrinkKind.BEER)))
        assertFalse(Drinks.showBreakdown(emptyList()))
    }
}

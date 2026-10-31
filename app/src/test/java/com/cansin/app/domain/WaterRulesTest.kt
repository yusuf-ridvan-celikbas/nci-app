package com.cansin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WaterRulesTest {
    @Test
    fun countsBeersAfterLastWater() {
        assertEquals(3, WaterRules.beersSinceWater(listOf(1, 2, 3), null))
        assertEquals(1, WaterRules.beersSinceWater(listOf(1, 2, 3), 2))
        assertEquals(0, WaterRules.beersSinceWater(listOf(1, 2), 5))
    }

    @Test
    fun nudgesEveryNBeers() {
        assertEquals(false, WaterRules.shouldNudge(true, 2, 1, null))
        assertEquals(true, WaterRules.shouldNudge(true, 2, 2, null))
        assertEquals(true, WaterRules.shouldNudge(true, 2, 5, null))
        assertEquals(false, WaterRules.shouldNudge(false, 2, 5, null))
    }

    @Test
    fun dismissHidesUntilNextBeer() {
        assertEquals(false, WaterRules.shouldNudge(true, 2, 2, dismissedAt = 2))
        assertEquals(true, WaterRules.shouldNudge(true, 2, 3, dismissedAt = 2))
    }

    @Test
    fun clampsInterval() {
        assertEquals(1, WaterRules.clamp(0))
        assertEquals(10, WaterRules.clamp(99))
    }
}

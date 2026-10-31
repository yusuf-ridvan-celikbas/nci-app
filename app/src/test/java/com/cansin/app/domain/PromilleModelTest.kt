package com.cansin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromilleModelTest {
    private val min = PromilleModel.MINUTE_MS
    private val t0 = 1_000_000_000L
    private val male = BodyProfile(Sex.MALE, 30, 180, 80, License.NORMAL)

    @Test
    fun gramsOfAlcohol() {
        assertEquals(19.725, PromilleModel.grams(500, 5.0), 1e-9)
    }

    @Test
    fun watsonBodyWater() {
        assertEquals(45.8202, PromilleModel.totalBodyWater(male), 1e-3)
        val female = male.copy(sex = Sex.FEMALE)
        assertEquals(-2.097 + 0.1069 * 180 + 0.2466 * 80, PromilleModel.totalBodyWater(female), 1e-9)
        assertEquals(PromilleModel.totalBodyWater(female), PromilleModel.totalBodyWater(male.copy(sex = Sex.UNSPECIFIED)), 0.0)
    }

    @Test
    fun unspecifiedSexIsMoreConservativeThanMale() {
        val drinks = listOf(Drink(t0, null, 500, 5.0))
        val asMale = PromilleModel.estimate(male, drinks, t0 + 50 * min)
        val asUnspecified = PromilleModel.estimate(male.copy(sex = Sex.UNSPECIFIED), drinks, t0 + 50 * min)
        assertTrue(asUnspecified.peak > asMale.peak)
    }

    @Test
    fun singleBeerPeakAndTimeToZero() {
        val result = PromilleModel.estimate(male, listOf(Drink(t0, null, 500, 5.0)), t0 + 50 * min)
        assertEquals(0.21, result.peak, 1e-9)
        assertEquals(0.21, result.current, 1e-9)
        val zeroAt = result.zeroAt!!
        assertTrue(zeroAt in t0 + 135 * min..t0 + 140 * min)
    }

    @Test
    fun beforeAndAfterDrinkingIsZero() {
        val drinks = listOf(Drink(t0, t0 + 10 * min, 330, 5.0))
        assertEquals(0.0, PromilleModel.estimate(male, drinks, t0 - min).current, 0.0)
        val later = PromilleModel.estimate(male, drinks, t0 + 24 * 60 * min)
        assertEquals(0.0, later.current, 0.0)
        assertNull(later.zeroAt)
        assertTrue(later.peak > 0)
    }

    @Test
    fun noDrinksGivesZero() {
        assertEquals(PromilleEstimate(0.0, 0.0, null), PromilleModel.estimate(male, emptyList(), t0))
    }

    @Test
    fun missingFinishUsesTwentyMinutesOrNextBeer() {
        val drinks = listOf(Drink(t0, null, 500, 5.0), Drink(t0 + 10 * min, null, 500, 5.0), Drink(t0 + 60 * min, null, 500, 5.0))
        assertEquals(t0 + 10 * min, PromilleModel.drinkEnd(drinks, 0))
        assertEquals(t0 + 30 * min, PromilleModel.drinkEnd(drinks, 1))
        assertEquals(t0 + 80 * min, PromilleModel.drinkEnd(drinks, 2))
        assertEquals(t0, PromilleModel.drinkEnd(listOf(Drink(t0, t0 - min, 500, 5.0)), 0))
    }

    @Test
    fun moreBeersRaiseEstimate() {
        val one = PromilleModel.estimate(male, listOf(Drink(t0, null, 500, 5.0)), t0 + 60 * min)
        val two = PromilleModel.estimate(male, listOf(Drink(t0, null, 500, 5.0), Drink(t0 + 5 * min, null, 500, 5.0)), t0 + 60 * min)
        assertTrue(two.current > one.current)
    }

    @Test
    fun promilleRoundsUp() {
        assertEquals(0.10, PromilleModel.toPromille(0.1055), 1e-9)
        assertEquals(0.11, PromilleModel.toPromille(0.10551), 1e-9)
        assertEquals(0.0, PromilleModel.toPromille(0.0), 0.0)
    }
}

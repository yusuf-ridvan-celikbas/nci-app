package com.cansin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WarningRulesTest {
    @Test
    fun englishShowsOnlyGeneric() {
        assertEquals(listOf(Warning.Generic), WarningRules.warnings(3.5, License.NORMAL, turkish = false, hour = 22))
    }

    @Test
    fun overAndUnderLimitPerLicense() {
        assertEquals(listOf(Warning.OverLimit(0.50)), WarningRules.warnings(0.50, License.NORMAL, true, 22))
        assertEquals(listOf(Warning.UnderLimit), WarningRules.warnings(0.49, License.NORMAL, true, 22))
        assertEquals(listOf(Warning.OverLimit(0.20)), WarningRules.warnings(0.20, License.CANDIDATE, true, 22))
        assertEquals(listOf(Warning.OverLimit(0.20)), WarningRules.warnings(0.25, License.COMMERCIAL, true, 22))
        assertEquals(emptyList<Warning>(), WarningRules.warnings(0.0, License.NORMAL, true, 22))
    }

    @Test
    fun noLicenseGetsTimeGreeting() {
        assertEquals(listOf(Warning.Hello(Greeting.MORNING)), WarningRules.warnings(0.3, License.NONE, true, 5))
        assertEquals(Greeting.MORNING, WarningRules.greeting(11))
        assertEquals(Greeting.DAY, WarningRules.greeting(12))
        assertEquals(Greeting.DAY, WarningRules.greeting(17))
        assertEquals(Greeting.EVENING, WarningRules.greeting(18))
        assertEquals(Greeting.EVENING, WarningRules.greeting(4))
    }

    @Test
    fun healthCautionsForAnyLicense() {
        assertEquals(listOf(Warning.Hello(Greeting.EVENING), Warning.SlowDown), WarningRules.warnings(1.50, License.NONE, true, 23))
        assertEquals(listOf(Warning.OverLimit(0.50), Warning.Poisoning), WarningRules.warnings(3.00, License.NORMAL, true, 23))
    }

    @Test
    fun noneLicenseHasNoLimit() {
        assertNull(TrDrivingLaw.limits[License.NONE])
    }

    @Test
    fun profileParsingRespectsRanges() {
        assertEquals(18, BodyProfile.parse(" 18 ", BodyProfile.AGE))
        assertNull(BodyProfile.parse("17", BodyProfile.AGE))
        assertNull(BodyProfile.parse("80.5", BodyProfile.WEIGHT_KG))
        assertNull(BodyProfile.parse("1000", BodyProfile.HEIGHT_CM))
        assertEquals(false, BodyProfile(Sex.MALE, 30, 119, 80, License.NONE).isValid)
    }
}

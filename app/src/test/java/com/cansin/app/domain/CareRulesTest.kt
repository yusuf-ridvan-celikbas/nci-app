package com.cansin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CareRulesTest {
    private val now = 10_000_000L

    private fun pick(hour: Int, beers: Int = 1, dismissed: CareDismissals = CareDismissals(), active: Boolean = true) =
        CareRules.pick(true, active, now, hour, lateHour = 1, myBeers = beers, dismissed = dismissed)

    @Test
    fun lateWindowWrapsMidnight() {
        assertTrue(CareRules.isLate(23, 22))
        assertTrue(CareRules.isLate(3, 22))
        assertFalse(CareRules.isLate(6, 22))
        assertFalse(CareRules.isLate(21, 22))
        assertTrue(CareRules.isLate(1, 1))
        assertTrue(CareRules.isLate(5, 0))
        assertFalse(CareRules.isLate(23, 0))
        assertFalse(CareRules.isLate(0, 1))
    }

    @Test
    fun picksOneMessage() {
        assertEquals(CareKind.LATE, pick(hour = 2, beers = 7))
        assertEquals(CareKind.SLOW_DOWN, pick(hour = 20, beers = 5))
        assertNull(pick(hour = 20, beers = 4))
        assertNull(pick(hour = 2, active = false))
        assertNull(CareRules.pick(false, true, 0, 2, 1, 9, CareDismissals()))
    }

    @Test
    fun dismissalsSnoozeThenReturn() {
        assertEquals(CareKind.SLOW_DOWN, pick(hour = 2, beers = 6, dismissed = CareDismissals(lateAt = now - 1_000)))
        assertEquals(CareKind.LATE, pick(hour = 2, dismissed = CareDismissals(lateAt = now - CareRules.LATE_SNOOZE_MS)))
        assertNull(pick(hour = 20, beers = 6, dismissed = CareDismissals(slowDownAtBeers = 6)))
        assertEquals(CareKind.SLOW_DOWN, pick(hour = 20, beers = 7, dismissed = CareDismissals(slowDownAtBeers = 6)))
    }

    @Test
    fun cleansHomeAndHour() {
        assertEquals("Kadıköy", CareRules.cleanHome("  Kadıköy\n "))
        assertNull(CareRules.cleanHome("   "))
        assertEquals(CareRules.MAX_HOME_LENGTH, CareRules.cleanHome("x".repeat(500))!!.length)
        assertEquals(CareRules.DEFAULT_LATE_HOUR, CareRules.clampLateHour(12))
        assertEquals(23, CareRules.clampLateHour(23))
    }
}

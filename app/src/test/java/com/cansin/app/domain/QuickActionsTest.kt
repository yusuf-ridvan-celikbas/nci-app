package com.cansin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickActionsTest {
    @Test
    fun parsesOnlyKnownKeys() {
        assertEquals(QuickAction.BEER, QuickAction.parse("beer"))
        assertEquals(QuickAction.TAB, QuickAction.parse("tab"))
        listOf(null, "", "BEER", "beer ", "delete").forEach { assertNull(it, QuickAction.parse(it)) }
    }

    @Test
    fun tileAddsOnlyWhenSafe() {
        assertTrue(QuickAction.tileAddsDirectly(ageConfirmed = true, versus = false))
        assertFalse(QuickAction.tileAddsDirectly(ageConfirmed = false, versus = false))
        assertFalse(QuickAction.tileAddsDirectly(ageConfirmed = true, versus = true))
    }
}

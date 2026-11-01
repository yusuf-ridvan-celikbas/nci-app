package com.cansin.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinScreenRulesTest {
    @Test
    fun unpinsOnlyWhenPinnedWithoutSharedSession() {
        assertTrue(PinScreenRules.shouldAutoUnpin(pinned = true, sharedSessionOpen = false))
        assertFalse(PinScreenRules.shouldAutoUnpin(pinned = true, sharedSessionOpen = true))
        assertFalse(PinScreenRules.shouldAutoUnpin(pinned = false, sharedSessionOpen = false))
    }
}

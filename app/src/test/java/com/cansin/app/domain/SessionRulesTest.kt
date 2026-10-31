package com.cansin.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRulesTest {
    private val sixHours = SessionRules.DEFAULT_INACTIVITY_MS

    @Test
    fun notStaleJustBeforeTimeout() = assertFalse(SessionRules.isStale(0, sixHours - 1))

    @Test
    fun staleAtTimeout() = assertTrue(SessionRules.isStale(0, sixHours))

    @Test
    fun clockGoingBackwardsIsNotStale() = assertFalse(SessionRules.isStale(10_000, 0))

    @Test
    fun customTimeoutIsRespected() = assertTrue(SessionRules.isStale(0, 60_000, timeoutMs = 60_000))
}

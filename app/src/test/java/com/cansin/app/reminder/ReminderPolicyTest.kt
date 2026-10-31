package com.cansin.app.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPolicyTest {
    @Test
    fun intervalIsClampedTo5To60() {
        assertEquals(5, ReminderPolicy.clampInterval(1))
        assertEquals(60, ReminderPolicy.clampInterval(600))
        assertEquals(10, ReminderPolicy.clampInterval(10))
    }

    @Test
    fun nextAtUsesClampedMinutes() {
        assertEquals(1_000L + 10 * 60_000, ReminderPolicy.nextAt(1_000, 10))
        assertEquals(1_000L + 5 * 60_000, ReminderPolicy.nextAt(1_000, 0))
    }

    @Test
    fun scheduleOnlyWhenEnabledAndNotStopped() {
        assertTrue(ReminderPolicy.shouldSchedule(enabled = true, sessionId = 1, stoppedSessionId = null))
        assertTrue(ReminderPolicy.shouldSchedule(enabled = true, sessionId = 2, stoppedSessionId = 1))
        assertFalse(ReminderPolicy.shouldSchedule(enabled = true, sessionId = 1, stoppedSessionId = 1))
        assertFalse(ReminderPolicy.shouldSchedule(enabled = false, sessionId = 1, stoppedSessionId = null))
    }

    @Test
    fun actionsOnlyApplyToOpenSession() {
        assertTrue(ReminderPolicy.isCurrent(3, 3))
        assertFalse(ReminderPolicy.isCurrent(3, 4))
        assertFalse(ReminderPolicy.isCurrent(3, null))
    }

    @Test
    fun missedReminderAfterRebootFiresShortlyAfterRestore() {
        assertEquals(10_000L + ReminderPolicy.RESTORE_DELAY_MS, ReminderPolicy.restoreAt(nextAt = 5_000, now = 10_000))
        assertEquals(500_000L, ReminderPolicy.restoreAt(nextAt = 500_000, now = 10_000))
    }
}

package com.cansin.app.reminder

import com.cansin.app.prefs.Settings

object ReminderPolicy {
    const val RESTORE_DELAY_MS = 60_000L

    fun clampInterval(minutes: Int): Int =
        minutes.coerceIn(Settings.MIN_REMINDER_INTERVAL_MIN, Settings.MAX_REMINDER_INTERVAL_MIN)

    fun nextAt(now: Long, intervalMin: Int): Long = now + clampInterval(intervalMin) * 60_000L

    fun shouldSchedule(enabled: Boolean, sessionId: Long, stoppedSessionId: Long?): Boolean =
        enabled && sessionId != stoppedSessionId

    fun isCurrent(actionSessionId: Long, openSessionId: Long?): Boolean =
        openSessionId != null && actionSessionId == openSessionId

    fun restoreAt(nextAt: Long, now: Long): Long = maxOf(nextAt, now + RESTORE_DELAY_MS)
}

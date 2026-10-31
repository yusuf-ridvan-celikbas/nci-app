package com.cansin.app.domain

/** Kind, non-pushy care messages on the counter (P16). One at a time; never praise or reward amounts. */
enum class CareKind { LATE, SLOW_DOWN }

data class CareDismissals(val lateAt: Long? = null, val slowDownAtBeers: Int? = null)

object CareRules {
    const val SLOW_DOWN_BEERS = 5
    const val DEFAULT_LATE_HOUR = 1
    const val MORNING_HOUR = 6
    const val LATE_SNOOZE_MS = 60 * 60_000L
    const val MAX_HOME_LENGTH = 100
    val LATE_HOURS = listOf(22, 23, 0, 1, 2, 3)

    fun clampLateHour(hour: Int): Int = hour.takeIf { it in LATE_HOURS } ?: DEFAULT_LATE_HOUR

    /** Late = from [lateHour] until 06:00, across midnight when lateHour is in the evening. */
    fun isLate(hour: Int, lateHour: Int): Boolean =
        if (lateHour >= MORNING_HOUR) hour >= lateHour || hour < MORNING_HOUR else hour in lateHour until MORNING_HOUR

    fun pick(
        enabled: Boolean,
        sessionActive: Boolean,
        now: Long,
        hour: Int,
        lateHour: Int,
        myBeers: Int,
        dismissed: CareDismissals,
    ): CareKind? {
        if (!enabled || !sessionActive) return null
        val lateSnoozed = dismissed.lateAt != null && now - dismissed.lateAt in 0 until LATE_SNOOZE_MS
        if (isLate(hour, lateHour) && !lateSnoozed) return CareKind.LATE
        if (myBeers >= SLOW_DOWN_BEERS && dismissed.slowDownAtBeers != myBeers) return CareKind.SLOW_DOWN
        return null
    }

    /** Free text for the maps app: trimmed, ≤100 chars, no control characters. Blank → null. */
    fun cleanHome(raw: String?): String? =
        raw?.filterNot { Character.isISOControl(it) }?.trim()?.take(MAX_HOME_LENGTH)?.takeIf { it.isNotBlank() }
}

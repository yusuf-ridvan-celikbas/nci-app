package com.cansin.app.domain

import com.cansin.app.data.Note
import com.cansin.app.data.Session
import com.cansin.app.data.Side

/** Kind, non-pushy care messages on the counter (P16/P17). One at a time; never praise or reward amounts. */
enum class CareKind { BEER_LIMIT, BUDGET, LATE, SLOW_DOWN }

data class CareDismissals(
    val lateAt: Long? = null,
    val slowDownAtBeers: Int? = null,
    val limitAtBeers: Int? = null,
    val budgetAtSpend: Long? = null,
)

data class CareInput(
    val enabled: Boolean,
    val sessionActive: Boolean,
    val now: Long,
    val hour: Int,
    val lateHour: Int = CareRules.DEFAULT_LATE_HOUR,
    val myBeers: Int = 0,
    val beerLimit: Int? = null,
    val budgetMinor: Long? = null,
    val mySpendMinor: Long = 0,
)

object CareRules {
    const val SLOW_DOWN_BEERS = 5
    const val DEFAULT_LATE_HOUR = 1
    const val MORNING_HOUR = 6
    const val LATE_SNOOZE_MS = 60 * 60_000L
    const val MAX_HOME_LENGTH = 100
    const val MAX_BEER_LIMIT = 20
    val LATE_HOURS = listOf(22, 23, 0, 1, 2, 3)

    fun clampLateHour(hour: Int): Int = hour.takeIf { it in LATE_HOURS } ?: DEFAULT_LATE_HOUR

    fun cleanBeerLimit(value: Int?): Int? = value?.takeIf { it in 1..MAX_BEER_LIMIT }

    fun cleanBudget(value: Long?): Long? = value?.takeIf { it in 1..NoteRules.MAX_AMOUNT_MINOR }

    /** Late = from [lateHour] until 06:00, across midnight when lateHour is in the evening. */
    fun isLate(hour: Int, lateHour: Int): Boolean =
        if (lateHour >= MORNING_HOUR) hour >= lateHour || hour < MORNING_HOUR else hour in lateHour until MORNING_HOUR

    /**
     * Limits are the user's own choice, so they come first and work even with kind reminders off.
     * "Not now" hides each until its number changes (late: for an hour).
     */
    fun pick(input: CareInput, dismissed: CareDismissals): CareKind? = with(input) {
        if (!sessionActive) return null
        if (beerLimit != null && myBeers >= beerLimit && dismissed.limitAtBeers != myBeers) return CareKind.BEER_LIMIT
        if (budgetMinor != null && mySpendMinor >= budgetMinor && dismissed.budgetAtSpend != mySpendMinor) return CareKind.BUDGET
        if (!enabled) return null
        val lateSnoozed = dismissed.lateAt != null && now - dismissed.lateAt in 0 until LATE_SNOOZE_MS
        if (isLate(hour, lateHour) && !lateSnoozed) return CareKind.LATE
        if (myBeers >= SLOW_DOWN_BEERS && dismissed.slowDownAtBeers != myBeers) return CareKind.SLOW_DOWN
        return null
    }

    /** Solo: whole tab. Versus with my side: my side + shared items. Versus without a side: whole tab. */
    fun mySpend(session: Session, notes: List<Note>): Long {
        val totals = VersusRules.totals(notes)
        return when (VersusRules.meSide(session)) {
            Side.RED -> totals.red + totals.shared
            Side.BLUE -> totals.blue + totals.shared
            else -> totals.total
        }
    }

    /** Free text for the maps app: no control characters, trimmed, ≤100 chars. Blank → null. */
    fun cleanHome(raw: String?): String? =
        raw?.filterNot { Character.isISOControl(it) }?.trim()?.take(MAX_HOME_LENGTH)?.takeIf { it.isNotBlank() }
}

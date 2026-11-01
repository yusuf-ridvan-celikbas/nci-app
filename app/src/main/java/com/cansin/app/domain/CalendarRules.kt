package com.cansin.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One ended session as the calendar sees it: when it started and what the tab came to. */
data class DaySession(val sessionId: Long, val startedAt: Long, val spentMinor: Long)

data class MonthSummary(val sessions: Int, val spentMinor: Long, val avgSpentMinor: Long?)

object CalendarRules {
    const val CHART_MONTHS = 6

    fun day(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** Cells for a month grid, weeks starting on [firstDay]; null pads the first week. */
    fun grid(month: YearMonth, firstDay: DayOfWeek): List<LocalDate?> {
        val lead = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
        return List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    }

    fun byDay(sessions: List<DaySession>, zone: ZoneId): Map<LocalDate, List<DaySession>> =
        sessions.sortedBy { it.startedAt }.groupBy { day(it.startedAt, zone) }

    fun inMonth(sessions: List<DaySession>, month: YearMonth, zone: ZoneId): List<DaySession> =
        sessions.filter { YearMonth.from(day(it.startedAt, zone)) == month }

    /** Average only over sessions that had any amount on the tab. */
    fun summary(sessions: List<DaySession>): MonthSummary {
        val spent = sessions.sumOf { it.spentMinor }
        val paid = sessions.count { it.spentMinor > 0 }
        return MonthSummary(sessions.size, spent, if (paid > 0) spent / paid else null)
    }

    /** Spent per month for the [count] months ending at [last], oldest first. */
    fun spentPerMonth(sessions: List<DaySession>, last: YearMonth, zone: ZoneId, count: Int = CHART_MONTHS): List<Pair<YearMonth, Long>> {
        val totals = sessions.groupBy { YearMonth.from(day(it.startedAt, zone)) }.mapValues { (_, v) -> v.sumOf { it.spentMinor } }
        return (count - 1 downTo 0).map { last.minusMonths(it.toLong()) }.map { it to (totals[it] ?: 0L) }
    }

    /** Months the user can page through: from the first session's month (or [now]) up to [now]. */
    fun range(sessions: List<DaySession>, now: YearMonth, zone: ZoneId): ClosedRange<YearMonth> {
        val first = sessions.minOfOrNull { it.startedAt }?.let { YearMonth.from(day(it, zone)) } ?: now
        return minOf(first, now)..now
    }
}

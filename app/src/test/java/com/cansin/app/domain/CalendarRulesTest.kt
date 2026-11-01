package com.cansin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarRulesTest {
    private val zone: ZoneId = ZoneOffset.ofHours(3)
    private fun at(y: Int, m: Int, d: Int, h: Int = 21) = LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()
    private val nov = YearMonth.of(2026, 11)

    @Test
    fun gridPadsToFirstWeekday() {
        // 1 Nov 2026 is a Sunday.
        val monday = CalendarRules.grid(nov, DayOfWeek.MONDAY)
        assertEquals(6, monday.takeWhile { it == null }.size)
        assertEquals(LocalDate.of(2026, 11, 1), monday[6])
        assertEquals(36, monday.size)
        val sunday = CalendarRules.grid(nov, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 1), sunday[0])
        assertEquals(30, sunday.size)
    }

    @Test
    fun sessionsGroupByStartDayInZone() {
        val late = DaySession(1, at(2026, 11, 3, 23), 0)
        val afterMidnight = DaySession(2, at(2026, 11, 4, 1), 0)
        val days = CalendarRules.byDay(listOf(afterMidnight, late), zone)
        assertEquals(listOf(1L), days[LocalDate.of(2026, 11, 3)]!!.map { it.sessionId })
        assertEquals(listOf(2L), days[LocalDate.of(2026, 11, 4)]!!.map { it.sessionId })
    }

    @Test
    fun summaryAveragesOnlyPaidSessions() {
        val s = CalendarRules.summary(listOf(DaySession(1, 0, 10_000), DaySession(2, 0, 0), DaySession(3, 0, 5_000)))
        assertEquals(3, s.sessions)
        assertEquals(15_000, s.spentMinor)
        assertEquals(7_500L, s.avgSpentMinor)
        assertNull(CalendarRules.summary(listOf(DaySession(1, 0, 0))).avgSpentMinor)
        assertEquals(MonthSummary(0, 0, null), CalendarRules.summary(emptyList()))
    }

    @Test
    fun inMonthAndSpentPerMonth() {
        val list = listOf(
            DaySession(1, at(2026, 9, 10), 2_000),
            DaySession(2, at(2026, 11, 1), 1_000),
            DaySession(3, at(2026, 11, 20), 500),
            DaySession(4, at(2025, 11, 5), 9_999),
        )
        assertEquals(listOf(2L, 3L), CalendarRules.inMonth(list, nov, zone).map { it.sessionId })
        val chart = CalendarRules.spentPerMonth(list, nov, zone, 3)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), nov), chart.map { it.first })
        assertEquals(listOf(2_000L, 0L, 1_500L), chart.map { it.second })
    }

    @Test
    fun rangeStartsAtFirstSession() {
        val list = listOf(DaySession(1, at(2026, 2, 1), 0), DaySession(2, at(2026, 10, 1), 0))
        assertEquals(YearMonth.of(2026, 2)..nov, CalendarRules.range(list, nov, zone))
        assertEquals(nov..nov, CalendarRules.range(emptyList(), nov, zone))
    }
}

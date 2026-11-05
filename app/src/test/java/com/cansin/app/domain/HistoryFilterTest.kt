package com.cansin.app.domain

import com.cansin.app.data.Session
import com.cansin.app.data.SessionMode
import com.cansin.app.data.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HistoryFilterTest {
    private val utc = ZoneOffset.UTC
    private val today = LocalDate.parse("2026-11-06")
    private fun at(date: String) = LocalDate.parse(date).atTime(21, 0).toInstant(utc).toEpochMilli()
    private fun night(id: Long, date: String, mode: SessionMode = SessionMode.SOLO, drinks: Int = 2, spent: Long = 0) =
        SessionSummary(Session(id = id, startedAt = at(date), endedAt = at(date) + 3_600_000, mode = mode), drinks, 0, 0, spent)

    private val nights = listOf(
        night(1, "2026-11-02", drinks = 3, spent = 45_000),
        night(2, "2026-10-15", SessionMode.VERSUS, drinks = 5),
        night(3, "2026-08-01", SessionMode.GROUP, drinks = 7, spent = 90_000),
        night(4, "2025-12-31", drinks = 1, spent = 10_000),
    )
    private val search = mapOf(1L to "Efes nuts", 2L to "Ali Can", 3L to "Işık Guinness", 4L to "")

    private fun ids(f: HistoryFilter) = HistoryFilterRules.apply(nights, f, search, today, utc).map { it.session.id }

    @Test
    fun periods() {
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(HistoryFilter()))
        assertEquals(listOf(1L), ids(HistoryFilter(period = HistoryPeriod.THIS_MONTH)))
        assertEquals(listOf(1L, 2L), ids(HistoryFilter(period = HistoryPeriod.LAST_3_MONTHS)))
        assertEquals(listOf(1L, 2L, 3L), ids(HistoryFilter(period = HistoryPeriod.THIS_YEAR)))
        val custom = HistoryFilter(period = HistoryPeriod.CUSTOM, from = LocalDate.parse("2026-10-15"), to = LocalDate.parse("2025-12-31"))
        assertEquals(listOf(2L, 3L, 4L), ids(custom)) // ends included, reversed range fixed
    }

    @Test
    fun modesSearchTabAndSort() {
        assertEquals(listOf(2L, 3L), ids(HistoryFilter(modes = setOf(SessionMode.VERSUS, SessionMode.GROUP))))
        assertEquals(listOf(1L), ids(HistoryFilter(query = " EFES ")))
        assertEquals(listOf(3L), ids(HistoryFilter(query = "ışık")))
        assertEquals(listOf(3L), ids(HistoryFilter(query = "isik")))
        assertEquals(listOf(1L, 3L, 4L), ids(HistoryFilter(onlyWithTab = true)))
        assertEquals(listOf(3L, 1L, 4L, 2L), ids(HistoryFilter(sort = HistorySort.MOST_SPENT)))
        assertEquals(listOf(3L, 2L, 1L, 4L), ids(HistoryFilter(sort = HistorySort.MOST_DRINKS)))
        assertEquals(listOf(4L, 3L, 2L, 1L), ids(HistoryFilter(sort = HistorySort.OLDEST)))
    }

    @Test
    fun dateTextIsSearchable() {
        val r = HistoryFilterRules.apply(nights, HistoryFilter(query = "ekim"), search, today, utc) { if (it == at("2026-10-15")) "15 Ekim 2026" else "" }
        assertEquals(listOf(2L), r.map { it.session.id })
    }

    @Test
    fun activeWhenAnythingIsSet() {
        assertFalse(HistoryFilter().isActive)
        assertTrue(HistoryFilter(sort = HistorySort.OLDEST).isActive)
        assertTrue(HistoryFilter(query = "x").isActive)
    }
}

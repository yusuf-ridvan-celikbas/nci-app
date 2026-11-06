package com.cansin.app.domain

import com.cansin.app.data.SessionMode
import com.cansin.app.data.SessionSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

enum class HistoryPeriod { ALL, THIS_MONTH, LAST_3_MONTHS, THIS_YEAR, CUSTOM }

enum class HistorySort { NEWEST, OLDEST, MOST_SPENT, MOST_DRINKS }

/** P30 History list filter (list only; Stats stay complete). Empty [modes] = every kind of night. */
data class HistoryFilter(
    val period: HistoryPeriod = HistoryPeriod.ALL,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val modes: Set<SessionMode> = emptySet(),
    val query: String = "",
    val onlyWithTab: Boolean = false,
    val sort: HistorySort = HistorySort.NEWEST,
) {
    val isActive: Boolean get() = this != HistoryFilter()
}

object HistoryFilterRules {
    private val turkish = Locale.forLanguageTag("tr")
    const val MAX_QUERY = 50

    private val fold = mapOf('ı' to 'i', 'ş' to 's', 'ç' to 'c', 'ğ' to 'g', 'ö' to 'o', 'ü' to 'u', 'â' to 'a', 'î' to 'i', 'û' to 'u')

    /** Lower-case for search (Turkish-aware); Turkish letters folded so "isik" finds "Işık". */
    fun normalize(text: String): String =
        text.trim().lowercase(turkish).replace("i̇", "i").map { fold[it] ?: it }.joinToString("")

    fun range(filter: HistoryFilter, today: LocalDate): ClosedRange<LocalDate>? = when (filter.period) {
        HistoryPeriod.ALL -> null
        HistoryPeriod.THIS_MONTH -> today.withDayOfMonth(1)..today
        HistoryPeriod.LAST_3_MONTHS -> today.minusMonths(3)..today
        HistoryPeriod.THIS_YEAR -> today.withDayOfYear(1)..today
        HistoryPeriod.CUSTOM -> {
            val a = filter.from ?: LocalDate.MIN
            val b = filter.to ?: LocalDate.MAX
            if (a <= b) a..b else b..a
        }
    }

    /**
     * [searchText] = names, seat names and notes per night; [dateText] formats a night's date so "12.10" or
     * "Ekim" also match.
     */
    fun apply(
        summaries: List<SessionSummary>,
        filter: HistoryFilter,
        searchText: Map<Long, String>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        dateText: (Long) -> String = { "" },
    ): List<SessionSummary> {
        val range = range(filter, today)
        val q = normalize(filter.query.take(MAX_QUERY))
        val matches = summaries.filter { s ->
            val day = Instant.ofEpochMilli(s.session.startedAt).atZone(zone).toLocalDate()
            (range == null || day in range) &&
                (filter.modes.isEmpty() || s.session.mode in filter.modes) &&
                (!filter.onlyWithTab || s.spentMinor > 0) &&
                (q.isEmpty() || normalize(searchText[s.session.id].orEmpty() + " " + dateText(s.session.startedAt)).contains(q))
        }
        val newest = compareByDescending<SessionSummary> { it.session.startedAt }
        return when (filter.sort) {
            HistorySort.NEWEST -> matches.sortedWith(newest)
            HistorySort.OLDEST -> matches.sortedBy { it.session.startedAt }
            HistorySort.MOST_SPENT -> matches.sortedWith(compareByDescending<SessionSummary> { it.spentMinor }.then(newest))
            HistorySort.MOST_DRINKS -> matches.sortedWith(compareByDescending<SessionSummary> { it.beers }.then(newest))
        }
    }
}

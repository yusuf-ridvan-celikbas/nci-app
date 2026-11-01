package com.cansin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.domain.CalendarRules
import com.cansin.app.domain.DaySession
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/** Month grid that marks days with a session (presence only, no counts) plus plain tab totals. */
@Composable
fun MonthCalendar(sessions: List<DaySession>, onOpen: (Long) -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val locale = LocalConfiguration.current.locales[0]
    val now = remember { YearMonth.now(zone) }
    val range = remember(sessions) { CalendarRules.range(sessions, now, zone) }
    var shown by rememberSaveable { mutableStateOf(now.toString()) }
    val month = YearMonth.parse(shown).coerceIn(range.start, range.endInclusive)
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val byDay = remember(sessions) { CalendarRules.byDay(sessions, zone) }
    val amount = rememberAmountFormat()
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { shown = month.minusMonths(1).toString(); picked = null }, enabled = month > range.start) {
                Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.calendar_prev))
            }
            Text(
                month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " " + month.year,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { shown = month.plusMonths(1).toString(); picked = null }, enabled = month < range.endInclusive) {
                Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.calendar_next))
            }
        }
        Row {
            (0 until 7).map { firstDay.plus(it.toLong()) }.forEach {
                Text(
                    it.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val pickedDay = picked?.let(LocalDate::parse)
        CalendarRules.grid(month, firstDay).chunked(7).forEach { week ->
            Row {
                week.forEach { day ->
                    DayCell(day, day != null && byDay.containsKey(day), day != null && day == pickedDay) { picked = day.toString() }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
        pickedDay?.let { byDay[it] }?.forEach { s ->
            val time = formatDate(s.startedAt) + " " + formatTime(s.startedAt)
            Text(
                if (s.spentMinor > 0) time + " · " + amount(s.spentMinor) else time,
                Modifier.fillMaxWidth().clickable { onOpen(s.sessionId) }.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val summary = CalendarRules.summary(CalendarRules.inMonth(sessions, month, zone))
        Text(pluralStringResource(R.plurals.calendar_month_sessions, summary.sessions, summary.sessions))
        if (summary.spentMinor > 0) Text(stringResource(R.string.calendar_month_spent, amount(summary.spentMinor)))
        summary.avgSpentMinor?.let { Text(stringResource(R.string.calendar_avg_spent, amount(it))) }
        val chart = CalendarRules.spentPerMonth(sessions, now, zone)
        if (chart.any { it.second > 0 }) {
            BarChart(
                stringResource(R.string.calendar_chart_spent),
                chart.map { it.second.toDouble() },
                stringResource(R.string.stats_chart_description, chart.joinToString { amount(it.second) }),
            )
        }
    }
}

@Composable
private fun RowScope.DayCell(day: LocalDate?, marked: Boolean, selected: Boolean, onPick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val number = day?.dayOfMonth ?: 0
    val label = if (marked) stringResource(R.string.calendar_day_session, number) else number.toString()
    Box(
        Modifier
            .weight(1f)
            .aspectRatio(1f)
            .padding(2.dp)
            .then(if (day == null) Modifier else Modifier.semantics { contentDescription = label })
            .then(
                when {
                    selected -> Modifier.background(colors.primary, CircleShape)
                    marked -> Modifier.background(colors.primaryContainer, CircleShape)
                    else -> Modifier
                },
            )
            .then(if (marked) Modifier.clickable(onClick = onPick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (day != null) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    selected -> colors.onPrimary
                    marked -> colors.onPrimaryContainer
                    else -> colors.onSurfaceVariant
                },
            )
        }
    }
}

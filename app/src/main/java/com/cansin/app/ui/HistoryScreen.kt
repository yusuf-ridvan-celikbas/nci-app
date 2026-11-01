package com.cansin.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.data.SessionMode
import com.cansin.app.data.SessionSummary
import com.cansin.app.domain.DaySession
import com.cansin.app.domain.Overview
import com.cansin.app.domain.StatsRules
import java.util.Date

@Composable
internal fun formatDate(millis: Long): String =
    android.text.format.DateFormat.getMediumDateFormat(LocalContext.current).format(Date(millis))

@Composable
internal fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return stringResource(R.string.duration_value, minutes / 60, minutes % 60)
}

@Composable
internal fun formatDecimal(value: Double): String = String.format(LocalConfiguration.current.locales[0], "%.1f", value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.history_sessions)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.history_stats)) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.history_friends)) })
            }
            when (tab) {
                0 -> {
                    val summaries by viewModel.summaries.collectAsState()
                    summaries?.let { SessionList(it, onOpen) }
                }
                1 -> {
                    val overview by viewModel.overview.collectAsState()
                    val days by viewModel.daySessions.collectAsState()
                    overview?.let { Stats(it, days.orEmpty(), onOpen) }
                }
                else -> FriendResultsTab(viewModel)
            }
        }
    }
}

@Composable
private fun SessionList(summaries: List<SessionSummary>, onOpen: (Long) -> Unit) {
    if (summaries.isEmpty()) {
        Text(stringResource(R.string.history_empty), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val amount = rememberAmountFormat()
    LazyColumn {
        items(summaries, key = { it.session.id }) { summary ->
            val session = summary.session
            Column(Modifier.fillMaxWidth().clickable { onOpen(session.id) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                val date = formatDate(session.startedAt) + " " + formatTime(session.startedAt)
                Text(
                    if (session.mode == SessionMode.VERSUS) stringResource(R.string.history_versus_row, date) else date,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    listOfNotNull(
                        pluralStringResource(R.plurals.beers_count, summary.beers, summary.beers),
                        StatsRules.durationMs(session)?.let { formatDuration(it) },
                        pluralStringResource(R.plurals.notes_count, summary.notes, summary.notes),
                        summary.spentMinor.takeIf { it > 0 }?.let { amount(it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Stats(overview: Overview, days: List<DaySession>, onOpen: (Long) -> Unit) {
    if (days.isEmpty()) {
        Text(stringResource(R.string.history_empty), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthCalendar(days, onOpen)
        if (overview.sessions == 0) return@Column
        HorizontalDivider()
        Text(pluralStringResource(R.plurals.stats_sessions, overview.sessions, overview.sessions))
        overview.avgBeers?.let { Text(stringResource(R.string.stats_avg_beers, formatDecimal(it))) }
        overview.avgPaceMin?.let { Text(stringResource(R.string.stats_pace, formatDecimal(it))) }
        overview.waterPerBeer?.let { Text(stringResource(R.string.stats_water_ratio, formatDecimal(it))) }
        val beers = overview.perSession.map { it.beers.toDouble() }
        BarChart(
            stringResource(R.string.stats_chart_beers),
            beers,
            stringResource(R.string.stats_chart_description, beers.joinToString { it.toInt().toString() }),
        )
        val peaks = overview.perSession.mapNotNull { it.peak }
        if (peaks.isNotEmpty()) {
            val labels = peaks.map { formatPromille(it) }
            BarChart(
                stringResource(R.string.stats_chart_peak),
                peaks,
                stringResource(R.string.stats_chart_description, labels.joinToString()),
            )
            Text(
                stringResource(R.string.stats_peak_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Disclaimer()
        }
    }
}

package com.cansin.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.data.SessionMode
import com.cansin.app.domain.HistoryFilter
import com.cansin.app.domain.HistoryPeriod
import com.cansin.app.domain.HistorySort
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@StringRes
private fun periodLabel(p: HistoryPeriod): Int = when (p) {
    HistoryPeriod.ALL -> R.string.period_all
    HistoryPeriod.THIS_MONTH -> R.string.period_this_month
    HistoryPeriod.LAST_3_MONTHS -> R.string.period_last_3
    HistoryPeriod.THIS_YEAR -> R.string.period_this_year
    HistoryPeriod.CUSTOM -> R.string.period_custom
}

@StringRes
private fun sortLabel(s: HistorySort): Int = when (s) {
    HistorySort.NEWEST -> R.string.sort_newest
    HistorySort.OLDEST -> R.string.sort_oldest
    HistorySort.MOST_SPENT -> R.string.sort_spent
    HistorySort.MOST_DRINKS -> R.string.sort_drinks
}

private val MODES = listOf(
    SessionMode.SOLO to R.string.mode_chip_solo,
    SessionMode.VERSUS to R.string.mode_chip_versus,
    SessionMode.GROUP to R.string.mode_chip_group,
)

/** P30: search, period, type, "with tab" and sort for the History list; shows how many nights match. */
@Composable
fun HistoryFilterBar(filter: HistoryFilter, count: Int, onChange: (HistoryFilter) -> Unit) {
    var periodMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var customRange by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = { onChange(filter.copy(query = it)) },
            placeholder = { Text(stringResource(R.string.history_search)) },
            singleLine = true,
            trailingIcon = {
                if (filter.query.isNotEmpty()) {
                    IconButton(onClick = { onChange(filter.copy(query = "")) }) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.search_clear))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                FilterChip(
                    selected = filter.period != HistoryPeriod.ALL,
                    onClick = { periodMenu = true },
                    label = { Text(stringResource(R.string.chip_menu, periodText(filter))) },
                )
                DropdownMenu(expanded = periodMenu, onDismissRequest = { periodMenu = false }) {
                    HistoryPeriod.entries.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(stringResource(periodLabel(p))) },
                            onClick = {
                                periodMenu = false
                                if (p == HistoryPeriod.CUSTOM) customRange = true else onChange(filter.copy(period = p, from = null, to = null))
                            },
                        )
                    }
                }
            }
            MODES.forEach { (mode, label) ->
                FilterChip(
                    selected = mode in filter.modes,
                    onClick = { onChange(filter.copy(modes = if (mode in filter.modes) filter.modes - mode else filter.modes + mode)) },
                    label = { Text(stringResource(label)) },
                )
            }
            FilterChip(
                selected = filter.onlyWithTab,
                onClick = { onChange(filter.copy(onlyWithTab = !filter.onlyWithTab)) },
                label = { Text(stringResource(R.string.filter_with_tab)) },
            )
            Box {
                FilterChip(
                    selected = filter.sort != HistorySort.NEWEST,
                    onClick = { sortMenu = true },
                    label = { Text(stringResource(R.string.chip_menu, stringResource(sortLabel(filter.sort)))) },
                )
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    HistorySort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(stringResource(sortLabel(s))) },
                            onClick = {
                                sortMenu = false
                                onChange(filter.copy(sort = s))
                            },
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (count == 0) stringResource(R.string.history_no_match) else pluralStringResource(R.plurals.history_count, count, count),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (filter.isActive) TextButton(onClick = { onChange(HistoryFilter()) }) { Text(stringResource(R.string.filter_clear)) }
        }
    }
    if (customRange) CustomRangeDialog(filter, onChange) { customRange = false }
}

@Composable
private fun periodText(filter: HistoryFilter): String {
    val from = filter.from
    val to = filter.to
    return if (filter.period == HistoryPeriod.CUSTOM && from != null && to != null) {
        stringResource(R.string.period_range, formatDay(from), formatDay(to))
    } else {
        stringResource(periodLabel(filter.period))
    }
}

@Composable
private fun formatDay(day: LocalDate): String = formatDate(day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())

/** M3 date range picker (dates in UTC millis, as the picker uses). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(filter: HistoryFilter, onChange: (HistoryFilter) -> Unit, onDismiss: () -> Unit) {
    fun toMillis(d: LocalDate?) = d?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    fun toDay(m: Long?) = m?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = toMillis(filter.from),
        initialSelectedEndDateMillis = toMillis(filter.to),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val from = toDay(state.selectedStartDateMillis)
                    onChange(filter.copy(period = HistoryPeriod.CUSTOM, from = from, to = toDay(state.selectedEndDateMillis) ?: from))
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}

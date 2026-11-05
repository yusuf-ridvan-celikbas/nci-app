package com.cansin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.data.SessionMode
import com.cansin.app.data.Side
import com.cansin.app.domain.BeerSize
import com.cansin.app.domain.SeatTimeRules
import com.cansin.app.domain.StatsRules
import com.cansin.app.domain.StorageRules
import com.cansin.app.domain.VersusRules

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(viewModel: HistoryViewModel, onBack: () -> Unit) {
    val detail by viewModel.detail.collectAsState()
    CompositionLocalProvider(LocalSeats provides detail?.seats.orEmpty()) { DetailContent(viewModel, detail, onBack) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailContent(viewModel: HistoryViewModel, detail: SessionDetail?, onBack: () -> Unit) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    val shareName by viewModel.shareName.collectAsState()
    val format = rememberAmountFormat()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.session_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
                actions = {
                    if (detail?.stats != null) {
                        IconButton(onClick = { sharing = true }) {
                            Icon(painterResource(R.drawable.ic_share), stringResource(R.string.share_result))
                        }
                    }
                    TextButton(onClick = { confirmDelete = true }, enabled = detail != null) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        val d = detail ?: return@Scaffold
        val session = d.session
        val versus = VersusRules.hasSides(session.mode)
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(formatDate(session.startedAt), style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(
                            formatTime(session.startedAt) + "–" + (session.endedAt?.let { formatTime(it) } ?: ""),
                            StatsRules.durationMs(session)?.let { formatDuration(it) },
                        ).joinToString(" · "),
                    )
                    val totals = session.totals
                    if (totals != null) {
                        // P27 compressed night: totals only.
                        val sides = StorageRules.decodeSides(totals.sides)
                        if (sides.isNotEmpty()) {
                            sides.forEach { c ->
                                val name = sideName(session, c.side)
                                Text(
                                    stringResource(
                                        R.string.group_detail_row,
                                        if (session.meSide == c.side) stringResource(R.string.versus_name_me, name) else name,
                                        pluralStringResource(R.plurals.beers_count, c.drinks, c.drinks),
                                        stringResource(R.string.water_count, c.water),
                                    ),
                                )
                            }
                        } else {
                            Text(stringResource(R.string.detail_beers, totals.drinks))
                            Text(stringResource(R.string.water_count, totals.water))
                        }
                        if (totals.spentMinor > 0) Text(stringResource(R.string.detail_spent_total, format(totals.spentMinor)))
                    } else if (session.mode == SessionMode.VERSUS) {
                        listOf(Side.RED, Side.BLUE).forEach { side ->
                            val name = sideName(session, side)
                            val count = d.beers.count { it.side == side }
                            Text(stringResource(R.string.tab_side_total, if (session.meSide == side) stringResource(R.string.versus_name_me, name) else name, count.toString()))
                        }
                    } else if (session.mode == SessionMode.GROUP) {
                        SeatTimeRules.times(d.seats, d.beers, d.water).forEach { t ->
                            val name = sideName(session, t.side)
                            val stay = t.firstAt?.let { first ->
                                val last = maxOf(t.lastDrinkAt ?: first, t.lastWaterAt ?: first)
                                formatDuration(last - first)
                            }
                            Text(
                                stringResource(
                                    R.string.group_detail_row,
                                    if (session.meSide == t.side) stringResource(R.string.versus_name_me, name) else name,
                                    pluralStringResource(R.plurals.beers_count, t.drinks, t.drinks),
                                    stringResource(R.string.water_count, t.water),
                                ) + (stay?.let { " · " + stringResource(R.string.group_at_table, it) } ?: ""),
                            )
                        }
                    } else {
                        Text(stringResource(R.string.detail_beers, d.beers.size))
                    }
                    if (totals == null && session.mode != SessionMode.GROUP) Text(stringResource(R.string.water_count, d.water.size))
                    d.stats?.paceMin?.let { Text(stringResource(R.string.stats_pace, formatDecimal(it))) }
                    d.stats?.peak?.let {
                        Text(stringResource(R.string.promille_peak, formatPromille(it)))
                        Disclaimer()
                    }
                    totals?.let {
                        Text(
                            stringResource(R.string.detail_compressed_note, formatDate(it.compressedAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
            items(d.beers, key = { "b${it.id}" }) { beer ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(formatTime(beer.at), Modifier.width(64.dp), style = MaterialTheme.typography.labelLarge)
                    Text(
                        drinkLabel(beer) + if (versus) " · " + sideName(session, beer.side) else "",
                    )
                }
            }
            if (d.notes.isNotEmpty()) item { HorizontalDivider() }
            items(d.notes, key = { "n${it.id}" }) { note ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(formatTime(note.editedTime ?: note.createdAt), Modifier.width(64.dp), style = MaterialTheme.typography.labelLarge)
                    Text(note.text, Modifier.weight(1f))
                    note.amountMinor?.let { Text(format(it)) }
                }
            }
        }
    }

    val current = detail
    if (sharing && current != null) {
        ShareResultDialog(
            initialName = shareName,
            build = { viewModel.shareCard(current, it) },
            onNameUsed = { viewModel.saveShareName(it) },
            onDismiss = { sharing = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_session_title)) },
            text = { Text(stringResource(R.string.delete_session_body)) },
            confirmButton = {
                TextButton(onClick = {
                    detail?.let { viewModel.delete(it.session.id) }
                    confirmDelete = false
                    onBack()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

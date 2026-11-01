package com.cansin.app.ui

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cansin.app.R
import com.cansin.app.data.Session
import com.cansin.app.data.Side
import com.cansin.app.domain.BeerSize
import com.cansin.app.domain.DrinkChoice
import com.cansin.app.domain.SeatTimeRules
import com.cansin.app.domain.SeatTimes
import kotlinx.coroutines.delay

private const val SLOW_TICK_MS = 30_000L

/** "Drinking together": one full-width row per person, in seat order. Plain numbers and times; no leader, no ranking. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    viewModel: GroupViewModel,
    onOpenTab: () -> Unit,
    onEditSeats: () -> Unit,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    resumed: Boolean,
    defaultDrink: DrinkChoice,
    beerSize: BeerSize,
    promilleCard: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val session = state.session ?: return
    val snackbar = remember { SnackbarHostState() }
    UndoEffect(viewModel.beerAdded, snackbar, viewModel::undo)
    UndoEffect(viewModel.waterAdded, snackbar, viewModel::undoWater, R.string.water_added)
    var picking by rememberSaveable { mutableStateOf<Side?>(null) }

    // Ticks only while visible: every second when a timer runs, else every 30 s for the "… ago" lines.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val fast = state.timerEnabled && state.timers.isNotEmpty()
    LaunchedEffect(resumed, fast) {
        while (resumed) {
            now = System.currentTimeMillis()
            val step = if (fast) 1_000L else SLOW_TICK_MS
            delay(step - now % step)
        }
    }

    CompositionLocalProvider(LocalSeats provides state.seats) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.group_title)) },
                    navigationIcon = {
                        IconButton(onClick = onOpenTab) {
                            Icon(painterResource(R.drawable.ic_receipt), stringResource(R.string.open_tab))
                        }
                    },
                    actions = {
                        IconButton(onClick = onTogglePin) {
                            Icon(
                                painterResource(if (pinned) R.drawable.ic_lock else R.drawable.ic_lock_open),
                                stringResource(if (pinned) R.string.unpin_screen else R.string.pin_screen),
                            )
                        }
                        IconButton(onClick = onEditSeats) {
                            Icon(painterResource(R.drawable.ic_versus), stringResource(R.string.group_edit))
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                OutlinedButton(
                    onClick = { viewModel.endSession() },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) { Text(stringResource(R.string.end_session)) }
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.times, key = { it.side.name }) { t ->
                    SeatRow(
                        session = session,
                        times = t,
                        now = now,
                        timerEnabled = state.timerEnabled,
                        timerStartedAt = state.timers[t.side],
                        onEdit = onEditSeats,
                        onAdd = { viewModel.add(t.side) },
                        onPick = { picking = t.side },
                        onRemove = { viewModel.removeLatest(t.side) },
                        onWater = { viewModel.addWater(t.side) },
                        onStart = { viewModel.startTimer(t.side) },
                        onStop = { viewModel.stopTimer(t.side) },
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.group_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        promilleCard()
                    }
                }
            }
        }
        picking?.let { side ->
            DrinkDialog(
                initial = defaultDrink,
                beer = beerSize,
                onConfirm = {
                    picking = null
                    viewModel.add(side, it)
                },
                onDismiss = { picking = null },
            )
        }
    }
}

@Composable
private fun ago(at: Long?, now: Long, none: Int, recent: Int, ago: Int): String {
    if (at == null) return stringResource(none)
    val minutes = SeatTimeRules.minutesSince(at, now)
    return if (minutes == 0L) stringResource(recent) else stringResource(ago, span(minutes))
}

@Composable
private fun span(minutes: Long): String =
    if (minutes < 60) stringResource(R.string.time_minutes, minutes.toInt())
    else stringResource(R.string.duration_value, (minutes / 60).toInt(), (minutes % 60).toInt())

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeatRow(
    session: Session,
    times: SeatTimes,
    now: Long,
    timerEnabled: Boolean,
    timerStartedAt: Long?,
    onEdit: () -> Unit,
    onAdd: () -> Unit,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    onWater: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val (seatBg, seatFg) = sideColor(times.side) ?: (MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary)
    val name = sideName(session, times.side)
    val me = session.meSide == times.side
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(seatBg))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (me) stringResource(R.string.versus_name_me, name) else name,
                        Modifier.weight(1f).clickable(onClick = onEdit),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(pluralStringResource(R.plurals.beers_count, times.drinks, times.drinks), style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    ago(times.lastDrinkAt, now, R.string.group_no_drinks, R.string.group_last_drink_now, R.string.group_last_drink) + " · " +
                        ago(times.lastWaterAt, now, R.string.group_no_water, R.string.group_last_water_now, R.string.group_last_water),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
                Text(
                    listOfNotNull(
                        times.firstAt?.let { stringResource(R.string.group_at_table, span(SeatTimeRules.minutesSince(it, now))) },
                        stringResource(R.string.group_water_count, times.water),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
                if (timerEnabled && (timerStartedAt != null || times.openDrink != null)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (timerStartedAt != null) {
                            val elapsed = DateUtils.formatElapsedTime(((now - timerStartedAt) / 1_000).coerceAtLeast(0))
                            Text(stringResource(R.string.timer_running) + " " + elapsed, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            OutlinedButton(onClick = onStop) { Text(stringResource(R.string.group_timer_stop)) }
                        } else {
                            Text(stringResource(R.string.setting_timer), Modifier.weight(1f), color = muted)
                            FilledTonalButton(onClick = onStart) { Text(stringResource(R.string.timer_idle)) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val removeDescription = stringResource(R.string.versus_remove, name)
                    OutlinedButton(
                        onClick = onRemove,
                        enabled = times.drinks > 0,
                        modifier = Modifier.semantics { contentDescription = removeDescription },
                    ) { Text(stringResource(R.string.minus_one), fontSize = 18.sp) }
                    AddDrink(name, seatBg, seatFg, onAdd, onPick)
                    val waterDescription = stringResource(R.string.group_water_add, name)
                    FilledTonalButton(onClick = onWater, modifier = Modifier.semantics { contentDescription = waterDescription }) {
                        Text(stringResource(R.string.group_add_water))
                    }
                }
            }
        }
    }
}

/** Tap = default drink, long-press = drink picker (same as the main counter). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.AddDrink(name: String, bg: Color, fg: Color, onAdd: () -> Unit, onPick: () -> Unit) {
    val description = stringResource(R.string.versus_add, name)
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = description,
                onLongClickLabel = stringResource(R.string.pick_size),
                onLongClick = onPick,
                onClick = onAdd,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.group_add_drink), color = fg, style = MaterialTheme.typography.titleMedium)
    }
}

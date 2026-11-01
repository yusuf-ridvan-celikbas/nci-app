package com.cansin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cansin.app.R
import com.cansin.app.data.Session
import com.cansin.app.domain.SeatTally

/** "Drinking together": one card per seat, in seat order. Neutral numbers only; no leader, no ranking. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    viewModel: GroupViewModel,
    onOpenSettings: () -> Unit,
    onOpenTab: () -> Unit,
    onEditSeats: () -> Unit,
    onOpenHistory: () -> Unit,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    promilleCard: @Composable () -> Unit,
    waterBar: @Composable (SnackbarHostState) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val session = state.session ?: return
    val snackbar = remember { SnackbarHostState() }
    UndoEffect(viewModel.beerAdded, snackbar, viewModel::undo)
    UndoEffect(viewModel.waterAdded, snackbar, viewModel::undoWater, R.string.water_added)

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
                        IconButton(onClick = onOpenHistory) {
                            Icon(painterResource(R.drawable.ic_history), stringResource(R.string.history_title))
                        }
                        IconButton(onClick = onEditSeats) {
                            Icon(painterResource(R.drawable.ic_versus), stringResource(R.string.group_edit))
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings))
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
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.tally.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { SeatCard(session, it, onEditSeats, viewModel) }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
                Text(
                    stringResource(R.string.group_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                waterBar(snackbar)
                promilleCard()
            }
        }
    }
}

@Composable
private fun RowScope.SeatCard(session: Session, seat: SeatTally, onEdit: () -> Unit, viewModel: GroupViewModel) {
    val (bg, fg) = sideColor(seat.side) ?: (Color.Unspecified to Color.Unspecified)
    val name = sideName(session, seat.side)
    val addDescription = stringResource(R.string.versus_add, name)
    val removeDescription = stringResource(R.string.versus_remove, name)
    val waterDescription = stringResource(R.string.group_water_add, name)
    Column(
        Modifier.weight(1f).background(bg, RoundedCornerShape(16.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            if (session.meSide == seat.side) stringResource(R.string.versus_name_me, name) else name,
            color = fg,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onEdit),
        )
        Text(seat.drinks.toString(), color = fg, style = MaterialTheme.typography.displaySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { viewModel.removeLatest(seat.side) },
                enabled = seat.drinks > 0,
                colors = ButtonDefaults.textButtonColors(contentColor = fg, disabledContentColor = fg.copy(alpha = 0.5f)),
                modifier = Modifier.semantics { contentDescription = removeDescription },
            ) { Text(stringResource(R.string.minus_one), fontSize = 20.sp) }
            Button(
                onClick = { viewModel.add(seat.side) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg),
                modifier = Modifier.size(64.dp).semantics { contentDescription = addDescription },
            ) { Text(stringResource(R.string.plus_one), fontSize = 22.sp) }
        }
        TextButton(
            onClick = { viewModel.addWater(seat.side) },
            colors = ButtonDefaults.textButtonColors(contentColor = fg),
            modifier = Modifier.semantics { contentDescription = waterDescription },
        ) { Text(stringResource(R.string.group_water, seat.water)) }
    }
}

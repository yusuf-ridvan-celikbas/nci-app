package com.cansin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cansin.app.R
import com.cansin.app.data.Session
import com.cansin.app.data.Side

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersusScreen(
    viewModel: VersusViewModel,
    onOpenSettings: () -> Unit,
    onOpenTab: () -> Unit,
    onEditSides: () -> Unit,
    onOpenHistory: () -> Unit,
    promilleCard: @Composable () -> Unit,
    waterBar: @Composable (SnackbarHostState) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val session = state.session ?: return
    val snackbar = remember { SnackbarHostState() }
    UndoEffect(viewModel.beerAdded, snackbar, viewModel::undo)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.versus_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenTab) {
                        Icon(painterResource(R.drawable.ic_receipt), stringResource(R.string.open_tab))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(painterResource(R.drawable.ic_history), stringResource(R.string.history_title))
                    }
                    IconButton(onClick = onEditSides) {
                        Icon(painterResource(R.drawable.ic_versus), stringResource(R.string.versus_edit))
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            SideHalf(session, Side.RED, state.red, onEditSides, viewModel::add, viewModel::removeLatest)
            SideHalf(session, Side.BLUE, state.blue, onEditSides, viewModel::add, viewModel::removeLatest)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                waterBar(snackbar)
                promilleCard()
            }
        }
    }
}

@Composable
private fun ColumnScope.SideHalf(
    session: Session,
    side: Side,
    count: Int,
    onEditSides: () -> Unit,
    onAdd: (Side) -> Unit,
    onRemove: (Side) -> Unit,
) {
    val (bg, fg) = sideColor(side) ?: (Color.Unspecified to Color.Unspecified)
    val name = sideName(session, side)
    val addDescription = stringResource(R.string.versus_add, name)
    val removeDescription = stringResource(R.string.versus_remove, name)
    Column(
        Modifier.fillMaxWidth().weight(1f).background(bg).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            if (session.meSide == side) stringResource(R.string.versus_name_me, name) else name,
            color = fg,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clickable(onClick = onEditSides),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            TextButton(
                onClick = { onRemove(side) },
                enabled = count > 0,
                colors = ButtonDefaults.textButtonColors(contentColor = fg, disabledContentColor = fg.copy(alpha = 0.5f)),
                modifier = Modifier.semantics { contentDescription = removeDescription },
            ) { Text(stringResource(R.string.minus_one), fontSize = 24.sp) }
            Text(count.toString(), color = fg, style = MaterialTheme.typography.displayLarge)
            Button(
                onClick = { onAdd(side) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg),
                modifier = Modifier.size(88.dp).semantics { contentDescription = addDescription },
            ) { Text(stringResource(R.string.plus_one), fontSize = 28.sp) }
        }
    }
}

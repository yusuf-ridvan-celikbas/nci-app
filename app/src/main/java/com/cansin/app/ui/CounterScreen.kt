package com.cansin.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cansin.app.R
import com.cansin.app.data.BeerEvent
import com.cansin.app.domain.BeerSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val UNDO_WINDOW_MS = 5_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterScreen(
    viewModel: CounterViewModel,
    title: String,
    defaultSize: BeerSize,
    onOpenSettings: () -> Unit,
    onOpenTab: () -> Unit,
    onStartVersus: () -> Unit,
    promilleCard: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var pickingSize by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    UndoEffect(viewModel.beerAdded, snackbar, viewModel::undo)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onOpenTab) {
                        Icon(painterResource(R.drawable.ic_receipt), stringResource(R.string.open_tab))
                    }
                },
                actions = {
                    IconButton(onClick = onStartVersus) {
                        Icon(painterResource(R.drawable.ic_versus), stringResource(R.string.versus_start))
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
                enabled = state.sessionActive,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            ) { Text(stringResource(R.string.end_session)) }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            promilleCard()
            Text(
                text = stringResource(if (state.sessionActive) R.string.counter_label else R.string.no_session),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = state.count.toString(), style = MaterialTheme.typography.displayLarge)
            AddButton(onClick = { viewModel.add() }, onLongClick = { pickingSize = true })
            Text(
                text = stringResource(R.string.counter_prompt),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            val removeDescription = stringResource(R.string.remove_beer)
            OutlinedButton(
                onClick = { viewModel.removeLatest() },
                enabled = state.count > 0,
                modifier = Modifier.semantics { contentDescription = removeDescription },
            ) { Text(stringResource(R.string.minus_one), fontSize = 24.sp) }
        }
    }

    if (pickingSize) {
        BeerSizeDialog(
            title = R.string.pick_size_title,
            confirm = R.string.action_add,
            initial = defaultSize,
            onConfirm = {
                pickingSize = false
                viewModel.add(it)
            },
            onDismiss = { pickingSize = false },
        )
    }
}

@Composable
internal fun UndoEffect(events: Flow<BeerEvent>, snackbar: SnackbarHostState, onUndo: (BeerEvent) -> Unit) {
    val addedText = stringResource(R.string.beer_added)
    val undoText = stringResource(R.string.undo)
    LaunchedEffect(events) {
        events.collectLatest { event ->
            coroutineScope {
                val timeout = launch {
                    delay(UNDO_WINDOW_MS)
                    snackbar.currentSnackbarData?.dismiss()
                }
                val result = snackbar.showSnackbar(addedText, undoText, duration = SnackbarDuration.Indefinite)
                timeout.cancel()
                if (result == SnackbarResult.ActionPerformed) onUndo(event)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddButton(onClick: () -> Unit, onLongClick: () -> Unit) {
    val addDescription = stringResource(R.string.add_beer)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(200.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = addDescription,
                onLongClickLabel = stringResource(R.string.pick_size),
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics { contentDescription = addDescription },
    ) {
        Text(stringResource(R.string.plus_one), fontSize = 64.sp, color = MaterialTheme.colorScheme.onPrimary)
    }
}

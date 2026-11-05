package com.cansin.app.ui

import androidx.annotation.StringRes
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.material3.TextButton
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import com.cansin.app.R
import com.cansin.app.domain.BeerSize
import com.cansin.app.domain.DrinkChoice
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
    defaultDrink: DrinkChoice,
    beerSize: BeerSize,
    onStartVersus: () -> Unit,
    promilleCard: @Composable () -> Unit,
    waterBar: @Composable (SnackbarHostState) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var pickingSize by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    UndoEffect(viewModel.beerAdded, snackbar, viewModel::undo)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    // Small question + people icon: opens Versus / Together.
                    TextButton(onClick = onStartVersus) {
                        Text(stringResource(R.string.with_friends_question), style = MaterialTheme.typography.labelMedium)
                        Icon(
                            painterResource(R.drawable.ic_versus),
                            contentDescription = null,
                            modifier = Modifier.padding(start = 6.dp),
                        )
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
        // Scrolls when cards (late / water nudge / promille) don't fit; stays centred when everything fits.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            promilleCard()
            Text(
                text = stringResource(if (state.sessionActive) R.string.counter_label else R.string.no_session),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = state.count.toString(), style = MaterialTheme.typography.displayLarge)
            drinkBreakdown(state.kinds)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AddButton(onClick = { viewModel.add() }, onLongClick = { pickingSize = true })
            Text(
                text = stringResource(R.string.counter_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            // Same row style as water: count · −1 · +1 (the big circle stays the main +).
            CountRow(
                label = stringResource(R.string.detail_beers, state.count),
                count = state.count,
                removeDescription = stringResource(R.string.remove_beer),
                addDescription = stringResource(R.string.add_beer),
                onRemove = { viewModel.removeLatest() },
                onAdd = { viewModel.add() },
            )
            waterBar(snackbar)
        }
        }
    }

    if (pickingSize) {
        DrinkDialog(
            initial = defaultDrink,
            beer = beerSize,
            onConfirm = {
                pickingSize = false
                viewModel.add(it)
            },
            onDismiss = { pickingSize = false },
        )
    }
}

@Composable
internal fun <T> UndoEffect(
    events: Flow<T>,
    snackbar: SnackbarHostState,
    onUndo: (T) -> Unit,
    @StringRes message: Int = R.string.beer_added,
) {
    val addedText = stringResource(message)
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
            .size(168.dp)
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
        Text(stringResource(R.string.plus_one), fontSize = 56.sp, color = MaterialTheme.colorScheme.onPrimary)
    }
}

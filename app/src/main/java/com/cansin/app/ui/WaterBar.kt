package com.cansin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.cansin.app.R

@Composable
fun WaterBar(state: WaterState, onAdd: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit, onAddSize: (Int) -> Unit = {}) {
    if (!state.available) return
    var picking by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (picking) WaterSizeDialog(onPick = { picking = false; onAddSize(it) }, onDismiss = { picking = false })
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.nudge) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.water_nudge),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    )
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) }
                }
            }
        }
        CountRow(
            label = stringResource(R.string.water_count, state.count),
            count = state.count,
            removeDescription = stringResource(R.string.water_remove),
            addDescription = stringResource(R.string.water_add),
            onRemove = onRemove,
            onAdd = onAdd,
            onLongAdd = { picking = true },
        )
    }
}

/** Long-press water +1: add a 250 ml / 500 ml / 1 L glass. */
@Composable
fun WaterSizeDialog(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.water_size_title)) },
        text = {
            Column {
                com.cansin.app.domain.WaterRules.SIZES_ML.forEach { ml ->
                    androidx.compose.material3.TextButton(onClick = { onPick(ml) }, modifier = Modifier.fillMaxWidth()) { Text(sizeText(ml)) }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** "Label: n" · −1 · +1, used for drinks and water on Tonight. −1 is off at 0. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun CountRow(label: String, count: Int, removeDescription: String, addDescription: String, onRemove: () -> Unit, onAdd: () -> Unit, onLongAdd: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        androidx.compose.material3.OutlinedButton(
            onClick = onRemove,
            enabled = count > 0,
            modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 72.dp).semantics { contentDescription = removeDescription },
        ) { Text(stringResource(R.string.minus_one), style = MaterialTheme.typography.titleMedium) }
        if (onLongAdd == null) {
            FilledTonalButton(
                onClick = onAdd,
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 72.dp).semantics { contentDescription = addDescription },
            ) { Text(stringResource(R.string.plus_one), style = MaterialTheme.typography.titleMedium) }
        } else {
            // Tap = default size, long-press = choose a size (same as the drink +1).
            androidx.compose.foundation.layout.Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 72.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .combinedClickable(
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClickLabel = addDescription,
                        onLongClickLabel = stringResource(R.string.water_size_title),
                        onLongClick = onLongAdd,
                        onClick = onAdd,
                    )
                    .semantics { contentDescription = addDescription },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.plus_one), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
    }
}

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
import com.cansin.app.R

@Composable
fun WaterBar(state: WaterState, onAdd: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    if (!state.available) return
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
        )
    }
}

/** "Label: n" · −1 · +1, used for drinks and water on Tonight. −1 is off at 0. */
@Composable
fun CountRow(label: String, count: Int, removeDescription: String, addDescription: String, onRemove: () -> Unit, onAdd: () -> Unit) {
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
        FilledTonalButton(
            onClick = onAdd,
            modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 72.dp).semantics { contentDescription = addDescription },
        ) { Text(stringResource(R.string.plus_one), style = MaterialTheme.typography.titleMedium) }
    }
}

package com.cansin.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.cansin.app.R
import com.cansin.app.domain.CareKind
import kotlinx.coroutines.delay

/** Ticks the care clock once a minute while the activity is resumed (no background work). */
@Composable
fun CareTicker(viewModel: CareViewModel, resumed: Boolean) {
    LaunchedEffect(resumed) {
        while (resumed) {
            viewModel.tick(System.currentTimeMillis())
            delay(60_000)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CareCard(state: CareState, onDismiss: (CareKind) -> Unit) {
    val kind = state.kind ?: return
    val context = LocalContext.current
    val stops = stringResource(R.string.care_stops_query)
    val taxi = stringResource(R.string.transport_query)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            val format = rememberAmountFormat()
            val text = when (kind) {
                CareKind.LATE -> stringResource(R.string.care_late)
                CareKind.SLOW_DOWN -> stringResource(R.string.care_slow_down)
                CareKind.BEER_LIMIT -> pluralStringResource(R.plurals.care_beer_limit, state.beerLimit ?: 0, state.beerLimit ?: 0)
                CareKind.BUDGET -> stringResource(R.string.care_budget, format(state.budgetMinor ?: 0))
            }
            Text(text, color = MaterialTheme.colorScheme.onTertiaryContainer)
            FlowRow {
                if (kind == CareKind.LATE || kind == CareKind.BEER_LIMIT) {
                    TextButton(onClick = { context.open(wayHome(state.home, stops)) }) { Text(stringResource(R.string.care_way_home)) }
                    TextButton(onClick = { context.open(("geo:0,0?q=" + Uri.encode(taxi)).toUri()) }) {
                        Text(stringResource(R.string.action_transport))
                    }
                }
                TextButton(onClick = { onDismiss(kind) }) { Text(stringResource(R.string.action_not_now)) }
            }
        }
    }
}

/** With a saved home: transit directions in the maps app. Without: nearby stops. The maps app does the network. */
private fun wayHome(home: String?, stopsQuery: String): Uri =
    if (home == null) {
        ("geo:0,0?q=" + Uri.encode(stopsQuery)).toUri()
    } else {
        ("https://www.google.com/maps/dir/?api=1&travelmode=transit&destination=" + Uri.encode(home)).toUri()
    }

private fun Context.open(uri: Uri) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
    }
}

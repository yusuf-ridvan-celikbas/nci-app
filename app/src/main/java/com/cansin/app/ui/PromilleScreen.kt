package com.cansin.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.cansin.app.R
import com.cansin.app.data.BeerEvent
import com.cansin.app.domain.BeerSize
import com.cansin.app.domain.Greeting
import com.cansin.app.domain.PromilleModel
import com.cansin.app.domain.Warning
import com.cansin.app.domain.WarningRules
import com.cansin.app.ui.theme.LocalAlertColors
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@Composable
fun PromilleTicker(viewModel: PromilleViewModel, resumed: Boolean) {
    LaunchedEffect(resumed) {
        while (resumed) {
            viewModel.tick()
            delay(PromilleModel.MINUTE_MS - System.currentTimeMillis() % PromilleModel.MINUTE_MS)
        }
    }
}

@Composable
private fun locale(): Locale = LocalConfiguration.current.locales[0]

@Composable
private fun formatPromille(value: Double): String = String.format(locale(), "%.2f", value)

@Composable
private fun warningsFor(state: PromilleState): List<Warning> {
    val estimate = state.estimate ?: return emptyList()
    val profile = state.profile ?: return emptyList()
    val hour = Instant.ofEpochMilli(state.now).atZone(ZoneId.systemDefault()).hour
    return WarningRules.warnings(estimate.current, profile.license, locale().language == "tr", hour)
}

@Composable
fun PromilleCard(state: PromilleState, onOpen: () -> Unit, onSetUp: () -> Unit, onDismissPrompt: () -> Unit) {
    if (!state.loaded) return
    val profile = state.profile
    if (profile == null) {
        if (state.promptDismissed) return
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.promille_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.profile_prompt_body), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissPrompt) { Text(stringResource(R.string.action_not_now)) }
                    TextButton(onClick = onSetUp) { Text(stringResource(R.string.action_set_up)) }
                }
            }
        }
        return
    }
    val estimate = state.estimate ?: return
    val openDescription = stringResource(R.string.promille_open)
    Card(Modifier.fillMaxWidth().clickable(onClickLabel = openDescription, onClick = onOpen)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.promille_current), style = MaterialTheme.typography.labelMedium)
            Text(
                stringResource(R.string.promille_value, formatPromille(estimate.current)),
                style = MaterialTheme.typography.headlineSmall,
            )
            warningsFor(state).firstOrNull()?.let { WarningBanner(it) }
            Disclaimer()
        }
    }
}

@Composable
private fun Disclaimer() {
    Text(
        stringResource(R.string.promille_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WarningBanner(warning: Warning) {
    val alerts = LocalAlertColors.current
    val context = LocalContext.current
    val (bg, fg) = when (warning) {
        is Warning.OverLimit, Warning.Poisoning -> alerts.danger to alerts.onDanger
        Warning.UnderLimit, Warning.SlowDown -> alerts.caution to alerts.onCaution
        else -> Color.Transparent to MaterialTheme.colorScheme.onSurface
    }
    val text = when (warning) {
        Warning.Generic -> stringResource(R.string.warning_generic)
        is Warning.OverLimit -> stringResource(R.string.warning_over, formatPromille(warning.limit))
        Warning.UnderLimit -> stringResource(R.string.warning_under)
        is Warning.Hello -> stringResource(
            when (warning.greeting) {
                Greeting.MORNING -> R.string.greeting_morning
                Greeting.DAY -> R.string.greeting_day
                Greeting.EVENING -> R.string.greeting_evening
            },
        )
        Warning.SlowDown -> stringResource(R.string.warning_slow_down)
        Warning.Poisoning -> stringResource(R.string.warning_poisoning)
    }
    val transportQuery = stringResource(R.string.transport_query)
    Column(
        Modifier.fillMaxWidth().background(bg, RoundedCornerShape(8.dp)).padding(12.dp),
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        when (warning) {
            is Warning.OverLimit -> TextButton(onClick = { context.open(("geo:0,0?q=" + Uri.encode(transportQuery)).toUri()) }) {
                Text(stringResource(R.string.action_transport), color = fg)
            }
            Warning.Poisoning -> TextButton(onClick = { context.dial() }) {
                Text(stringResource(R.string.action_call_112), color = fg)
            }
            else -> Unit
        }
    }
}

private fun Context.open(uri: Uri) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
    }
}

private fun Context.dial() {
    try {
        startActivity(Intent(Intent.ACTION_DIAL, "tel:112".toUri()))
    } catch (_: ActivityNotFoundException) {
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromilleScreen(viewModel: PromilleViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingProfile by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.promille_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Disclaimer()
                    state.estimate?.let { estimate ->
                        val value = stringResource(R.string.promille_value, formatPromille(estimate.current))
                        Text(stringResource(R.string.promille_current), style = MaterialTheme.typography.titleMedium)
                        Text(value, style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { contentDescription = value })
                        Text(stringResource(R.string.promille_peak, formatPromille(estimate.peak)))
                        estimate.zeroAt?.let { Text(stringResource(R.string.promille_zero_at, formatTime(it))) }
                        warningsFor(state).forEach { WarningBanner(it) }
                    }
                    if (state.beers.isEmpty()) Text(stringResource(R.string.promille_no_beers))
                    OutlinedButton(onClick = { editingProfile = true }) { Text(stringResource(R.string.profile_title)) }
                }
                HorizontalDivider()
            }
            items(state.beers, key = { it.id }) { beer ->
                Column(Modifier.fillMaxWidth().clickable { editing = beer.id }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(beerSizeLabel(BeerSize(beer.sizeMl, beer.abv)))
                    Text(
                        stringResource(R.string.beer_started, formatTime(beer.at)) +
                            (beer.finishedAt?.let { " · " + stringResource(R.string.beer_finished, formatTime(it)) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    state.beers.firstOrNull { it.id == editing }?.let { beer ->
        BeerEditDialog(
            beer = beer,
            onSave = {
                viewModel.updateBeer(it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    if (editingProfile) {
        ProfileDialog(
            initial = state.profile,
            onSave = {
                viewModel.saveProfile(it)
                editingProfile = false
            },
            onDismiss = { editingProfile = false },
        )
    }
}

private enum class BeerField { NONE, START, FINISH, SIZE }

@Composable
private fun BeerEditDialog(beer: BeerEvent, onSave: (BeerEvent) -> Unit, onDismiss: () -> Unit) {
    var at by rememberSaveable { mutableLongStateOf(beer.at) }
    var finishedAt by rememberSaveable { mutableStateOf(beer.finishedAt) }
    var size by rememberSaveable { mutableStateOf(beer.sizeMl to beer.abv) }
    var field by rememberSaveable { mutableStateOf(BeerField.NONE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_beer)) },
        text = {
            Column {
                TextButton(onClick = { field = BeerField.SIZE }) { Text(beerSizeLabel(BeerSize(size.first, size.second))) }
                TextButton(onClick = { field = BeerField.START }) { Text(stringResource(R.string.beer_started, formatTime(at))) }
                TextButton(onClick = { field = BeerField.FINISH }) {
                    Text(finishedAt?.let { stringResource(R.string.beer_finished, formatTime(it)) } ?: stringResource(R.string.beer_finished_unset))
                }
                if (finishedAt != null) {
                    TextButton(onClick = { finishedAt = null }) { Text(stringResource(R.string.action_not_finished)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(beer.copy(at = at, finishedAt = finishedAt, sizeMl = size.first, abv = size.second))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    when (field) {
        BeerField.START -> TimePickerDialog(at, beer.at, { at = it; field = BeerField.NONE }, { field = BeerField.NONE })
        BeerField.FINISH -> TimePickerDialog(
            finishedAt ?: at,
            finishedAt ?: at,
            { finishedAt = it; field = BeerField.NONE },
            { field = BeerField.NONE },
        )
        BeerField.SIZE -> BeerSizeDialog(
            title = R.string.edit_beer,
            confirm = R.string.action_save,
            initial = BeerSize(size.first, size.second),
            onConfirm = { size = it.sizeMl to it.abv; field = BeerField.NONE },
            onDismiss = { field = BeerField.NONE },
        )
        BeerField.NONE -> Unit
    }
}

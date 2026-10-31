package com.cansin.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.domain.AmountInput
import com.cansin.app.domain.CareRules
import com.cansin.app.domain.NoteRules
import androidx.compose.ui.text.input.KeyboardType
import com.cansin.app.prefs.Settings
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CareSection(settings: Settings, actions: SettingsActions) {
    var editingHome by rememberSaveable { mutableStateOf(false) }
    SectionHeader(R.string.section_care)
    SwitchRow(R.string.setting_care, settings.careEnabled, actions.onCareEnabled)
    Note(R.string.setting_care_note)
    if (settings.careEnabled) {
        Text(stringResource(R.string.setting_late_hour), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        val format = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        FlowRow {
            CareRules.LATE_HOURS.forEach { hour ->
                FilterChip(
                    selected = hour == settings.careLateHour,
                    onClick = { actions.onCareLateHour(hour) },
                    label = { Text(LocalTime.of(hour, 0).format(format)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { editingHome = true }.padding(vertical = 8.dp),
    ) {
        Text(stringResource(R.string.setting_home), style = MaterialTheme.typography.titleSmall)
        Text(settings.homePlace ?: stringResource(R.string.setting_home_not_set), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Note(R.string.setting_home_note)
    var editingLimits by rememberSaveable { mutableStateOf(false) }
    val format = rememberAmountFormat()
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { editingLimits = true }.padding(vertical = 8.dp),
    ) {
        Text(stringResource(R.string.setting_limits), style = MaterialTheme.typography.titleSmall)
        val parts = listOfNotNull(
            settings.beerLimit?.let { stringResource(R.string.setting_beer_limit) + ": " + it },
            settings.budgetMinor?.let { stringResource(R.string.setting_budget) + ": " + format(it) },
        )
        Text(parts.joinToString(" · ").ifEmpty { stringResource(R.string.setting_limits_off) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Note(R.string.setting_limits_note)
    if (editingLimits) {
        LimitsDialog(
            beers = settings.beerLimit,
            budgetMinor = settings.budgetMinor,
            onSave = { b, m -> actions.onLimits(b, m); editingLimits = false },
            onDismiss = { editingLimits = false },
        )
    }
    if (editingHome) {
        HomeDialog(
            initial = settings.homePlace.orEmpty(),
            onSave = { actions.onHomePlace(it); editingHome = false },
            onDismiss = { editingHome = false },
        )
    }
}

@Composable
private fun Note(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LimitsDialog(beers: Int?, budgetMinor: Long?, onSave: (Int?, Long?) -> Unit, onDismiss: () -> Unit) {
    val plain = rememberPlainAmountFormat()
    var beerText by rememberSaveable { mutableStateOf(beers?.toString().orEmpty()) }
    var budgetText by rememberSaveable { mutableStateOf(budgetMinor?.let(plain).orEmpty()) }
    val beerValue = beerText.trim().takeIf { it.isNotEmpty() }?.let { t -> t.toIntOrNull()?.let(CareRules::cleanBeerLimit) ?: -1 }
    val budgetValue = when (val parsed = NoteRules.parseAmount(budgetText)) {
        AmountInput.Empty -> null
        AmountInput.Invalid -> -1L
        is AmountInput.Valid -> CareRules.cleanBudget(parsed.minor) ?: -1L
    }
    val valid = beerValue != -1 && budgetValue != -1L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_limits)) },
        text = {
            Column {
                OutlinedTextField(
                    value = beerText,
                    onValueChange = { beerText = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.setting_beer_limit)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it.take(13) },
                    label = { Text(stringResource(R.string.setting_budget)) },
                    singleLine = true,
                    isError = budgetValue == -1L,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (!valid) {
                    Text(stringResource(R.string.limits_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(beerValue, budgetValue) }, enabled = valid) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun HomeDialog(initial: String, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_home)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(CareRules.MAX_HOME_LENGTH) },
                placeholder = { Text(stringResource(R.string.setting_home_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(CareRules.cleanHome(text)) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = {
            if (initial.isNotEmpty()) {
                TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.action_remove)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

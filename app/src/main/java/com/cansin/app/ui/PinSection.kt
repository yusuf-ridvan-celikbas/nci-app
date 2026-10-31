package com.cansin.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.domain.PinRules
import com.cansin.app.prefs.Settings
import com.cansin.app.system.AppLock
import com.cansin.app.system.PinCheck
import kotlinx.coroutines.launch

private enum class PinDialog { SET, CHANGE, REMOVE }

@Composable
internal fun PinSection(settings: Settings, lock: AppLock, onHidesWidget: (Boolean) -> Unit, onChanged: () -> Unit) {
    var dialog by rememberSaveable { mutableStateOf<PinDialog?>(null) }
    SectionHeader(R.string.section_lock)
    Text(
        stringResource(if (settings.pinSet) R.string.lock_on else R.string.lock_off),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row {
        if (settings.pinSet) {
            TextButton(onClick = { dialog = PinDialog.CHANGE }) { Text(stringResource(R.string.lock_change)) }
            TextButton(onClick = { dialog = PinDialog.REMOVE }) { Text(stringResource(R.string.lock_remove)) }
        } else {
            TextButton(onClick = { dialog = PinDialog.SET }) { Text(stringResource(R.string.lock_set)) }
        }
    }
    if (settings.pinSet) SwitchRow(R.string.lock_hide_widget, settings.pinHidesWidget, onHidesWidget)
    Text(
        stringResource(R.string.lock_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    dialog?.let { mode -> PinEditDialog(mode, lock, onDone = { dialog = null; onChanged() }, onDismiss = { dialog = null }) }
}

@Composable
private fun PinEditDialog(mode: PinDialog, lock: AppLock, onDone: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    val needsCurrent = mode != PinDialog.SET
    val needsNew = mode != PinDialog.REMOVE
    val ready = !busy && (!needsCurrent || PinRules.isValid(current)) && (!needsNew || (PinRules.isValid(new) && repeat.isNotEmpty()))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when (mode) {
                        PinDialog.SET -> R.string.lock_set
                        PinDialog.CHANGE -> R.string.lock_change
                        PinDialog.REMOVE -> R.string.lock_remove
                    },
                ),
            )
        },
        text = {
            Column {
                if (needsCurrent) PinField(R.string.lock_current, current) { current = it; error = null }
                if (needsNew) {
                    PinField(R.string.lock_new, new) { new = it; error = null }
                    PinField(R.string.lock_repeat, repeat) { repeat = it; error = null }
                }
                Text(
                    stringResource(error ?: R.string.lock_length),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = {
                    if (needsNew && new != repeat) {
                        error = R.string.lock_mismatch
                        return@TextButton
                    }
                    busy = true
                    scope.launch {
                        val check = if (needsCurrent) lock.check(current) else PinCheck.Ok
                        when (check) {
                            PinCheck.Ok -> {
                                if (needsNew) lock.setPin(new) else lock.removePin()
                                onDone()
                            }
                            is PinCheck.Wrong -> error = R.string.lock_wrong
                            is PinCheck.Wait -> error = R.string.lock_wait_short
                        }
                        busy = false
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PinField(label: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it in '0'..'9' }.take(PinRules.MAX_LENGTH)) },
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.padding(top = 4.dp),
    )
}

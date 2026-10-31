package com.cansin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.data.Note
import com.cansin.app.data.Session
import com.cansin.app.domain.AmountInput
import com.cansin.app.domain.NoteRules
import java.time.Instant
import java.time.ZoneId

@Composable
fun NoteEditDialog(
    note: Note,
    session: Session?,
    formatAmount: (Long) -> String,
    onSave: (Note) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(note.text) }
    var person by rememberSaveable { mutableStateOf(note.person.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(note.amountMinor?.let(formatAmount).orEmpty()) }
    var time by rememberSaveable { mutableLongStateOf(note.editedTime ?: note.createdAt) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var side by rememberSaveable { mutableStateOf(note.side) }
    val parsed = NoteRules.parseAmount(amount)
    val valid = NoteRules.cleanText(text) != null && parsed != AmountInput.Invalid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(Note.MAX_TEXT) },
                    label = { Text(stringResource(R.string.note_text)) },
                    isError = NoteRules.cleanText(text) == null,
                )
                OutlinedTextField(
                    value = person,
                    onValueChange = { person = it.take(Note.MAX_PERSON) },
                    label = { Text(stringResource(R.string.note_person)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.take(13) },
                    label = { Text(stringResource(R.string.note_amount)) },
                    isError = parsed == AmountInput.Invalid,
                    supportingText = if (parsed == AmountInput.Invalid) {
                        { Text(stringResource(R.string.invalid_amount)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (session != null) SideChips(session, side, { side = it })
                TextButton(onClick = { pickingTime = true }) {
                    Text(stringResource(R.string.note_time, formatTime(time)))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        note.copy(
                            text = text,
                            person = person,
                            amountMinor = (parsed as? AmountInput.Valid)?.minor,
                            editedTime = time.takeIf { it != note.createdAt },
                            side = side,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )

    if (pickingTime) {
        TimePickerDialog(
            initial = time,
            onPicked = { picked ->
                time = picked
                pickingTime = false
            },
            onDismiss = { pickingTime = false },
            reference = note.createdAt,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerDialog(initial: Long, reference: Long, onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(initial).atZone(zone)
    val state = rememberTimePickerState(
        initialHour = start.hour,
        initialMinute = start.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_time)) },
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = { onPicked(NoteRules.pickTime(reference, state.hour, state.minute, zone)) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

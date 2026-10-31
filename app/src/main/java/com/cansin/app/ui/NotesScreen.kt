package com.cansin.app.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.data.Note
import com.cansin.app.domain.AmountInput
import com.cansin.app.domain.NoteRules
import java.text.DecimalFormatSymbols
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(viewModel: NotesViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<Note?>(null) }
    val format = rememberAmountFormat()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = { QuickAdd(onAdd = viewModel::add) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.notes.isEmpty()) {
                Text(
                    stringResource(R.string.no_notes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                items(state.notes, key = { it.id }) { note ->
                    NoteRow(note, format) { editing = note }
                    HorizontalDivider()
                }
            }
            Text(
                stringResource(R.string.tab_total, format(state.totalMinor)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }

    editing?.let { note ->
        NoteEditDialog(
            note = note,
            formatAmount = format,
            onSave = { viewModel.update(it); editing = null },
            onDelete = { viewModel.delete(note); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
internal fun rememberAmountFormat(): (Long) -> String {
    val separator = DecimalFormatSymbols.getInstance().decimalSeparator
    return remember(separator) { { minor: Long -> NoteRules.formatAmount(minor, separator) } }
}

@Composable
internal fun formatTime(millis: Long): String = DateFormat.getTimeFormat(LocalContext.current).format(Date(millis))

@Composable
private fun NoteRow(note: Note, format: (Long) -> String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.width(64.dp)) {
            Text(formatTime(note.editedTime ?: note.createdAt), style = MaterialTheme.typography.labelLarge)
            if (note.editedTime != null) {
                Text(
                    stringResource(R.string.note_edited),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(note.text, style = MaterialTheme.typography.bodyLarge)
            note.person?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        note.amountMinor?.let { Text(format(it), style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun QuickAdd(onAdd: (String, Long?) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    val parsed = NoteRules.parseAmount(amount)

    fun submit() {
        if (NoteRules.cleanText(text) == null || parsed == AmountInput.Invalid) return
        onAdd(text, (parsed as? AmountInput.Valid)?.minor)
        text = ""
        amount = ""
    }

    Row(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(Note.MAX_TEXT) },
            placeholder = { Text(stringResource(R.string.note_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.take(13) },
            placeholder = { Text(stringResource(R.string.amount_hint)) },
            singleLine = true,
            isError = parsed == AmountInput.Invalid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.width(96.dp),
        )
        IconButton(onClick = ::submit) {
            Icon(painterResource(R.drawable.ic_send), stringResource(R.string.add_note))
        }
    }
}

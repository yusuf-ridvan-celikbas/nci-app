package com.cansin.app.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import com.cansin.app.domain.Currency
import com.cansin.app.domain.GroupRules
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.cansin.app.R
import com.cansin.app.data.Note
import com.cansin.app.data.Side
import com.cansin.app.domain.AmountInput
import com.cansin.app.domain.NoteRules
import java.text.DecimalFormatSymbols
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(viewModel: NotesViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    CompositionLocalProvider(LocalSeats provides state.seats) { NotesContent(viewModel, state, onBack) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesContent(viewModel: NotesViewModel, state: NotesState, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Note?>(null) }
    var settling by remember { mutableStateOf(false) }
    var pricing by remember { mutableStateOf<com.cansin.app.domain.DrinkLine?>(null) }
    val format = rememberAmountFormat()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_title)) },
                actions = {
                    TextButton(onClick = { settling = true }, enabled = state.totals.total > 0) { Text(stringResource(R.string.settle_title)) }
                },
            )
        },
        bottomBar = { QuickAdd(state, onAdd = viewModel::add) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.notes.isEmpty() && state.drinkLines.isEmpty()) {
                Text(
                    stringResource(R.string.no_notes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                if (state.drinkLines.isNotEmpty()) {
                    item(key = "drinks_header") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(stringResource(R.string.tab_drinks_auto), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(if (state.showPriceHint) R.string.tab_price_hint else R.string.tab_drinks_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(state.drinkLines, key = { "d" + it.kind + it.sizeMl + it.beerSide }) { line ->
                        DrinkLineRow(
                            line, state, format,
                            onTap = { pricing = line },
                            onRemove = { viewModel.removeDrink(line) },
                            onAdd = { viewModel.addDrink(line) },
                        )
                    }
                    item(key = "drinks_divider") { HorizontalDivider() }
                }
                items(state.notes, key = { it.id }) { note ->
                    NoteRow(note, state, format) { editing = note }
                    HorizontalDivider()
                }
            }
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                if (state.versus) {
                    val totals = state.totals
                    listOf(Side.RED to totals.red, Side.BLUE to totals.blue, null to totals.shared).forEach { (side, amount) ->
                        Text(stringResource(R.string.tab_side_total, sideName(state.session, side), format(amount)))
                    }
                }
                if (state.group) {
                    val (own, shared) = GroupRules.tab(state.seats, state.tab)
                    (GroupRules.ordered(state.seats).map { it.side to (own[it.side] ?: 0L) } + (null to shared)).forEach { (side, amount) ->
                        Text(stringResource(R.string.tab_side_total, sideName(state.session, side), format(amount)))
                    }
                }
                Text(stringResource(R.string.tab_total, format(state.totals.total)), style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (settling) SettleDialog(state, onDismiss = { settling = false })
    pricing?.let { line ->
        PriceDialog(
            title = stringResource(R.string.tab_price_title, stringResource(drinkName(line.kind)) + " · " + sizeText(line.sizeMl)),
            current = line.unitMinor,
            note = R.string.tab_price_note,
            clearLabel = R.string.tab_price_remove,
            onSave = { viewModel.setPrice(line, it); pricing = null },
            onDismiss = { pricing = null },
        )
    }

    editing?.let { note ->
        NoteEditDialog(
            note = note,
            session = state.session.takeIf { state.sided },
            formatAmount = rememberPlainAmountFormat(),
            onSave = { viewModel.update(it); editing = null },
            onDelete = { viewModel.delete(note); editing = null },
            onDismiss = { editing = null },
        )
    }
}

/** Currency chosen in Settings, provided once in MainActivity. */
internal val LocalCurrency = staticCompositionLocalOf { Currency.NONE }

/** Display format with the currency symbol. */
@Composable
internal fun rememberAmountFormat(): (Long) -> String {
    val separator = DecimalFormatSymbols.getInstance().decimalSeparator
    val currency = LocalCurrency.current
    val turkish = LocalConfiguration.current.locales[0].language == "tr"
    return remember(separator, currency, turkish) { { minor: Long -> currency.format(minor, separator, turkish) } }
}

/** Plain number for editable text fields (no symbol). */
@Composable
internal fun rememberPlainAmountFormat(): (Long) -> String {
    val separator = DecimalFormatSymbols.getInstance().decimalSeparator
    return remember(separator) { { minor: Long -> NoteRules.formatAmount(minor, separator) } }
}

@Composable
internal fun formatTime(millis: Long): String = DateFormat.getTimeFormat(LocalContext.current).format(Date(millis))

@Composable
private fun NoteRow(note: Note, state: NotesState, format: (Long) -> String, onClick: () -> Unit) {
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
            if (state.sided) {
                val colors = sideColor(note.side)
                Text(
                    sideName(state.session, note.side),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors?.second ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(colors?.first ?: Color.Transparent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        note.amountMinor?.let { Text(format(it), style = MaterialTheme.typography.bodyLarge) }
    }
}

/** Tab drink line: "Beer · 50 cl ×3", price each, sum, −1/+1. Tap = price for tonight (this pub). */
@Composable
private fun DrinkLineRow(line: com.cansin.app.domain.DrinkLine, state: NotesState, format: (Long) -> String, onTap: () -> Unit, onRemove: () -> Unit, onAdd: () -> Unit) {
    val name = stringResource(drinkName(line.kind))
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onTap).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.tab_drink_line, name, sizeText(line.sizeMl), line.count), style = MaterialTheme.typography.bodyLarge)
            Text(
                line.unitMinor?.let { stringResource(R.string.tab_price_each, format(it)) + " · " + format(line.totalMinor) }
                    ?: stringResource(R.string.tab_add_price),
                style = MaterialTheme.typography.bodySmall,
                color = if (line.unitMinor == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.sided) {
                val colors = sideColor(line.side)
                Text(
                    sideName(state.session, line.side),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors?.second ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .background(colors?.first ?: Color.Transparent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        val removeDescription = stringResource(R.string.tab_remove_drink, name)
        val addDescription = stringResource(R.string.tab_add_drink, name)
        androidx.compose.material3.OutlinedButton(
            onClick = onRemove,
            modifier = Modifier.semantics { contentDescription = removeDescription },
        ) { Text(stringResource(R.string.minus_one)) }
        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
        androidx.compose.material3.FilledTonalButton(
            onClick = onAdd,
            modifier = Modifier.semantics { contentDescription = addDescription },
        ) { Text(stringResource(R.string.plus_one)) }
    }
}

@Composable
private fun QuickAdd(state: NotesState, onAdd: (String, Long?, Side?) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var side by rememberSaveable { mutableStateOf<Side?>(null) }
    var amount by rememberSaveable { mutableStateOf("") }
    val parsed = NoteRules.parseAmount(amount)

    fun submit() {
        if (NoteRules.cleanText(text) == null || parsed == AmountInput.Invalid) return
        onAdd(text, (parsed as? AmountInput.Valid)?.minor, side)
        text = ""
        amount = ""
    }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(8.dp)) {
        if (state.sided) SideChips(state.session, side, { side = it })
        Row(
            modifier = Modifier.fillMaxWidth(),
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
}

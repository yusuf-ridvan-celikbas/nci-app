package com.cansin.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.domain.PriceSlot
import com.cansin.app.domain.AmountInput
import com.cansin.app.domain.NoteRules

/** P28 Settings → Drinks → Prices: an optional price per drink (beer per size); priced drinks go on the Tab by themselves. */
@Composable
fun PriceSection(prices: Map<PriceSlot, Long>, onPrice: (PriceSlot, Long?) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<PriceSlot?>(null) }
    val format = rememberAmountFormat()
    SectionHeader(R.string.section_prices)
    com.cansin.app.domain.PriceRules.VISIBLE.forEach { slot ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { editing = slot }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(slotLabel(slot), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(
                prices[slot]?.let(format) ?: stringResource(R.string.price_not_set),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Text(stringResource(R.string.price_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    editing?.let { slot ->
        PriceDialog(
            title = stringResource(R.string.price_title, slotLabel(slot)),
            current = prices[slot],
            onSave = { onPrice(slot, it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun slotLabel(slot: PriceSlot): String {
    val kind = slot.kind ?: return stringResource(R.string.price_water_label)
    val name = stringResource(drinkName(kind))
    return slot.sizeMl?.let { name + " · " + sizeText(it) } ?: name
}

/** Amount dialog for a price. Clear (or [clearLabel]) saves null. */
@Composable
internal fun PriceDialog(
    title: String,
    current: Long?,
    onSave: (Long?) -> Unit,
    onDismiss: () -> Unit,
    @androidx.annotation.StringRes note: Int? = null,
    @androidx.annotation.StringRes clearLabel: Int = R.string.price_clear,
    extra: (@Composable () -> Unit)? = null,
) {
    val plain = rememberPlainAmountFormat()
    var text by rememberSaveable { mutableStateOf(current?.let(plain).orEmpty()) }
    val parsed = NoteRules.parseAmount(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp)) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(13) },
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
                extra?.invoke()
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave((parsed as? AmountInput.Valid)?.minor?.takeIf { it > 0 }) },
                enabled = parsed != AmountInput.Invalid,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSave(null) }) { Text(stringResource(clearLabel)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

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
import com.cansin.app.data.DrinkKind
import com.cansin.app.domain.AmountInput
import com.cansin.app.domain.Drinks
import com.cansin.app.domain.NoteRules

/** P28 Settings → Drinks → Prices: an optional price per drink type; priced drinks go on the Tab by themselves. */
@Composable
fun PriceSection(prices: Map<DrinkKind, Long>, onPrice: (DrinkKind, Long?) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<DrinkKind?>(null) }
    val format = rememberAmountFormat()
    SectionHeader(R.string.section_prices)
    Drinks.DEFAULT_KINDS.forEach { kind ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { editing = kind }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(drinkName(kind)), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(
                prices[kind]?.let(format) ?: stringResource(R.string.price_not_set),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Text(stringResource(R.string.price_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    editing?.let { kind ->
        PriceDialog(kind, prices[kind], onSave = { onPrice(kind, it); editing = null }, onDismiss = { editing = null })
    }
}

@Composable
private fun PriceDialog(kind: DrinkKind, current: Long?, onSave: (Long?) -> Unit, onDismiss: () -> Unit) {
    val plain = rememberPlainAmountFormat()
    var text by rememberSaveable { mutableStateOf(current?.let(plain).orEmpty()) }
    val parsed = NoteRules.parseAmount(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.price_title, stringResource(drinkName(kind)))) },
        text = {
            Column {
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
                if (current != null) TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.price_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

package com.cansin.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.domain.BeerRules
import com.cansin.app.domain.BeerSize
import java.text.DecimalFormatSymbols

private const val CUSTOM = -1

@Composable
internal fun rememberDecimalSeparator(): Char = remember { DecimalFormatSymbols.getInstance().decimalSeparator }

@Composable
fun beerSizeLabel(size: BeerSize): String {
    val separator = rememberDecimalSeparator()
    return stringResource(
        R.string.beer_size_value,
        BeerRules.formatCl(size.sizeMl, separator),
        BeerRules.formatAbv(size.abv, separator),
    )
}

@Composable
fun BeerSizeDialog(
    @StringRes title: Int,
    @StringRes confirm: Int,
    initial: BeerSize,
    onConfirm: (BeerSize) -> Unit,
    onDismiss: () -> Unit,
) {
    val separator = rememberDecimalSeparator()
    var preset by rememberSaveable { mutableIntStateOf(initial.sizeMl.takeIf { it in BeerRules.PRESETS_ML } ?: CUSTOM) }
    var customCl by rememberSaveable {
        mutableStateOf(if (preset == CUSTOM) BeerRules.formatCl(initial.sizeMl, separator) else "")
    }
    var abvText by rememberSaveable { mutableStateOf(BeerRules.formatAbv(initial.abv, separator)) }
    val sizeMl = if (preset == CUSTOM) BeerRules.parseCl(customCl) else preset
    val abv = BeerRules.parseAbv(abvText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                Column(Modifier.selectableGroup()) {
                    (BeerRules.PRESETS_ML + CUSTOM).forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(option == preset, role = Role.RadioButton, onClick = { preset = option }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == preset, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (option == CUSTOM) stringResource(R.string.size_custom)
                                else stringResource(R.string.size_cl, BeerRules.formatCl(option, separator)),
                            )
                        }
                    }
                }
                if (preset == CUSTOM) {
                    NumberField(customCl, { customCl = it }, R.string.size_custom_cl, R.string.invalid_size, sizeMl == null)
                }
                NumberField(abvText, { abvText = it }, R.string.abv_label, R.string.invalid_abv, abv == null)
            }
        },
        confirmButton = {
            TextButton(
                enabled = sizeMl != null && abv != null,
                onClick = { if (sizeMl != null && abv != null) onConfirm(BeerSize(sizeMl, abv)) },
            ) { Text(stringResource(confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    @StringRes label: Int,
    @StringRes error: Int,
    invalid: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(5)) },
        label = { Text(stringResource(label)) },
        isError = invalid,
        supportingText = if (invalid) {
            { Text(stringResource(error)) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

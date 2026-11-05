package com.cansin.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.focusRequester
import com.cansin.app.R
import com.cansin.app.data.BeerEvent
import com.cansin.app.data.DrinkKind
import com.cansin.app.domain.BeerRules
import com.cansin.app.domain.BrandRules
import androidx.compose.foundation.layout.padding
import com.cansin.app.domain.BeerSize
import com.cansin.app.domain.DrinkChoice
import com.cansin.app.domain.Drinks

@StringRes
fun drinkName(kind: DrinkKind): Int = when (kind) {
    DrinkKind.BEER -> R.string.drink_beer
    DrinkKind.WINE -> R.string.drink_wine
    DrinkKind.RAKI -> R.string.drink_raki
    DrinkKind.SHOT -> R.string.drink_shot
    DrinkKind.COCKTAIL -> R.string.drink_cocktail
    DrinkKind.CUSTOM -> R.string.drink_custom
}

/** "Wine · 15 cl · 12.0%"; beer keeps the short size-only label. */
@Composable
fun drinkLabel(event: BeerEvent): String {
    val size = beerSizeLabel(BeerSize(event.sizeMl, event.abv))
    val brand = event.brand
    return when {
        brand != null -> "$brand · $size"
        event.kind == DrinkKind.BEER -> size
        else -> stringResource(drinkName(event.kind)) + " · " + size
    }
}

/** "Beer: 2 · Wine: 1", only when not all beer. */
@Composable
fun drinkBreakdown(kinds: List<DrinkKind>): String? {
    if (!Drinks.showBreakdown(kinds)) return null
    return Drinks.breakdown(kinds).map { (kind, n) -> stringResource(R.string.drink_count, stringResource(drinkName(kind)), n) }
        .joinToString(" · ")
}

/** Long-press +1: pick a kind (fills its usual size) and adjust size/alcohol if needed. */
@Composable
fun DrinkDialog(initial: DrinkChoice, beer: BeerSize, onConfirm: (DrinkChoice) -> Unit, onDismiss: () -> Unit) {
    var brand by rememberSaveable { mutableStateOf(initial.brand.orEmpty()) }
    val recentBrands = LocalRecentBrands.current
    val separator = rememberDecimalSeparator()
    var kind by rememberSaveable { mutableStateOf(initial.kind) }
    var clText by rememberSaveable { mutableStateOf(BeerRules.formatCl(initial.size.sizeMl, separator)) }
    var abvText by rememberSaveable { mutableStateOf(BeerRules.formatAbv(initial.size.abv, separator)) }
    val sizeMl = BeerRules.parseCl(clText)
    val abv = BeerRules.parseAbv(abvText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_size_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Column(Modifier.selectableGroup()) {
                    DrinkKind.entries.forEach { k ->
                        val name = stringResource(drinkName(k))
                        val text = if (k == DrinkKind.CUSTOM) name else name + " · " + beerSizeLabel(Drinks.preset(k, beer))
                        RadioRow(text, k == kind) {
                            kind = k
                            if (k != DrinkKind.CUSTOM) {
                                val p = Drinks.preset(k, beer)
                                clText = BeerRules.formatCl(p.sizeMl, separator)
                                abvText = BeerRules.formatAbv(p.abv, separator)
                            }
                        }
                    }
                }
                if (kind == DrinkKind.BEER) {
                    // Quick beer sizes: 33 cl, 50 cl, 1 L (Maß).
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        BeerRules.PRESETS_ML.forEach { ml ->
                            androidx.compose.material3.FilterChip(
                                selected = sizeMl == ml,
                                onClick = { clText = BeerRules.formatCl(ml, separator) },
                                label = { Text(sizeText(ml)) },
                                modifier = Modifier.semantics { role = androidx.compose.ui.semantics.Role.RadioButton },
                            )
                        }
                    }
                }
                NumberField(clText, { clText = it }, R.string.size_custom_cl, R.string.invalid_size, sizeMl == null)
                NumberField(abvText, { abvText = it }, R.string.abv_label, R.string.invalid_abv, abv == null)
                BrandPicker(brand, { brand = it }, if (kind == DrinkKind.BEER) BrandRules.choices(recentBrands) else recentBrands)
            }
        },
        confirmButton = {
            TextButton(
                enabled = sizeMl != null && abv != null,
                onClick = { if (sizeMl != null && abv != null) onConfirm(DrinkChoice(kind, BeerSize(sizeMl, abv), BrandRules.clean(brand))) },
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Brands used lately (newest first), provided by MainActivity for the pickers. */
val LocalRecentBrands = androidx.compose.runtime.staticCompositionLocalOf { emptyList<String>() }

/** "Brand (optional)": None + quick picks + free text. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun BrandPicker(value: String, onChange: (String) -> Unit, choices: List<String>) {
    // Tap = menu only (no keyboard): None, recent, suggestions, "Type another brand…".
    // Only that last item makes the field editable and opens the keyboard.
    var expanded by androidx.compose.runtime.remember { mutableStateOf(false) }
    var typing by androidx.compose.runtime.remember { mutableStateOf(false) }
    val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(typing) { if (typing) focus.requestFocus() }
    androidx.compose.material3.ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            expanded = it
            if (it) {
                typing = false
                keyboard?.hide()
            }
        },
        modifier = Modifier.padding(top = 12.dp),
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.take(BrandRules.MAX_LENGTH)) },
            readOnly = !typing,
            label = { Text(stringResource(R.string.brand_optional)) },
            placeholder = { Text(stringResource(R.string.brand_none)) },
            singleLine = true,
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .menuAnchor(
                    if (typing) androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryEditable
                    else androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                ),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.brand_none)) },
                onClick = {
                    onChange("")
                    expanded = false
                },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.brand_type_other)) },
                onClick = {
                    expanded = false
                    typing = true
                },
            )
            choices.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Settings: which kind the big +1 adds. */
@Composable
fun DefaultDrinkDialog(current: DrinkKind, beer: BeerSize, onPick: (DrinkKind) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_default_drink)) },
        text = {
            Column(Modifier.selectableGroup()) {
                Drinks.DEFAULT_KINDS.forEach { k ->
                    RadioRow(stringResource(drinkName(k)) + " · " + beerSizeLabel(Drinks.preset(k, beer)), k == current) { onPick(k) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

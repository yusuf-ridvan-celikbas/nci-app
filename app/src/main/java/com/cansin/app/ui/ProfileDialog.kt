package com.cansin.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.domain.BodyProfile
import com.cansin.app.domain.License
import com.cansin.app.domain.Sex

val Sex.label: Int
    @StringRes get() = when (this) {
        Sex.MALE -> R.string.sex_male
        Sex.FEMALE -> R.string.sex_female
        Sex.UNSPECIFIED -> R.string.sex_unspecified
    }

val License.label: Int
    @StringRes get() = when (this) {
        License.NONE -> R.string.license_none
        License.CANDIDATE -> R.string.license_candidate
        License.NORMAL -> R.string.license_normal
        License.COMMERCIAL -> R.string.license_commercial
    }

@Composable
fun ProfileDialog(initial: BodyProfile?, onSave: (BodyProfile?) -> Unit, onDismiss: () -> Unit) {
    var sex by rememberSaveable { mutableStateOf(initial?.sex ?: Sex.UNSPECIFIED) }
    var license by rememberSaveable { mutableStateOf(initial?.license ?: License.NONE) }
    var age by rememberSaveable { mutableStateOf(initial?.age?.toString().orEmpty()) }
    var height by rememberSaveable { mutableStateOf(initial?.heightCm?.toString().orEmpty()) }
    var weight by rememberSaveable { mutableStateOf(initial?.weightKg?.toString().orEmpty()) }
    val ageValue = BodyProfile.parse(age, BodyProfile.AGE)
    val heightValue = BodyProfile.parse(height, BodyProfile.HEIGHT_CM)
    val weightValue = BodyProfile.parse(weight, BodyProfile.WEIGHT_KG)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.profile_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.profile_sex), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Column(Modifier.selectableGroup()) {
                    Sex.entries.forEach { option -> RadioRow(stringResource(option.label), option == sex) { sex = option } }
                }
                NumberField(age, { age = it }, R.string.profile_age, R.string.invalid_range, age.isNotEmpty() && ageValue == null)
                NumberField(height, { height = it }, R.string.profile_height, R.string.invalid_range, height.isNotEmpty() && heightValue == null)
                NumberField(weight, { weight = it }, R.string.profile_weight, R.string.invalid_range, weight.isNotEmpty() && weightValue == null)
                Text(stringResource(R.string.profile_license), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Column(Modifier.selectableGroup()) {
                    License.entries.forEach { option -> RadioRow(stringResource(option.label), option == license) { license = option } }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = ageValue != null && heightValue != null && weightValue != null,
                onClick = {
                    if (ageValue != null && heightValue != null && weightValue != null) {
                        onSave(BodyProfile(sex, ageValue, heightValue, weightValue, license))
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.profile_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

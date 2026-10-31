package com.cansin.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.cansin.app.R

@Composable
fun DataSection(onExport: (includeProfile: Boolean) -> Unit, onDeleteAll: () -> Unit) {
    var exporting by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    SectionHeader(R.string.section_data)
    OutlinedButton(onClick = { exporting = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_data)) }
    OutlinedButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.delete_all_data), color = MaterialTheme.colorScheme.error)
    }

    if (exporting) {
        var includeProfile by rememberSaveable { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { exporting = false },
            title = { Text(stringResource(R.string.export_data)) },
            text = {
                Column {
                    Text(stringResource(R.string.export_body))
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(includeProfile, role = Role.Checkbox) { includeProfile = it },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = includeProfile, onCheckedChange = null)
                        Text(stringResource(R.string.export_include_profile))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    exporting = false
                    onExport(includeProfile)
                }) { Text(stringResource(R.string.action_share)) }
            },
            dismissButton = { TextButton(onClick = { exporting = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.delete_all_title)) },
            text = { Text(stringResource(R.string.delete_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    onDeleteAll()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

package com.cansin.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.domain.RestoreData
import com.cansin.app.domain.RestoreError
import com.cansin.app.domain.RestoreResult
import kotlinx.coroutines.launch

/** Some file managers label .json as plain text or octet-stream. */
private val BACKUP_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

@Composable
fun DataSection(
    onExport: (includeProfile: Boolean) -> Unit,
    onReadBackup: suspend (Uri) -> RestoreResult,
    onRestore: suspend (RestoreData) -> Unit,
    onDeleteAll: () -> Unit,
) {
    var exporting by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<RestoreData?>(null) }
    var message by rememberSaveable { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                when (val r = onReadBackup(uri)) {
                    is RestoreResult.Ok -> pending = r.data
                    is RestoreResult.Fail -> message = when (r.error) {
                        RestoreError.NOT_OURS -> R.string.restore_not_ours
                        RestoreError.NEWER_VERSION -> R.string.restore_newer
                        RestoreError.TOO_LARGE -> R.string.restore_too_large
                        RestoreError.INVALID -> R.string.restore_invalid
                    }
                }
            }
        }
    }
    SectionHeader(R.string.section_data)
    OutlinedButton(onClick = { exporting = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_data)) }
    OutlinedButton(onClick = { picker.launch(BACKUP_TYPES) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore_data)) }
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
    pending?.let { data ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.restore_title)) },
            text = {
                Text(stringResource(R.string.restore_body, pluralStringResource(R.plurals.stats_sessions, data.sessions.size, data.sessions.size)))
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        onRestore(data)
                        message = R.string.restore_done
                    }
                }) { Text(stringResource(R.string.restore_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    message?.let { res ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(stringResource(res)) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.action_continue)) } },
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

package com.cansin.app.ui

import android.text.format.Formatter
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.domain.CompressAfter
import com.cansin.app.domain.KeepHistory
import com.cansin.app.prefs.Settings
import com.cansin.app.system.StorageMaintenance
import kotlinx.coroutines.launch

/** A retention choice that would delete/compress [count] existing nights; asks first. */
private class PendingChoice(
    @StringRes val title: Int,
    @PluralsRes val body: Int,
    val count: Int,
    val apply: suspend () -> Unit,
)

/** P27 Settings → Privacy & data → Storage: usage, "Free up space", keep-history and compress choices. */
@Composable
fun StorageSection(
    settings: Settings,
    storage: StorageMaintenance,
    onKeepHistory: suspend (KeepHistory) -> Unit,
    onCompressAfter: suspend (CompressAfter) -> Unit,
    onExport: (includeProfile: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var freed by remember { mutableStateOf<Long?>(null) }
    var pending by remember { mutableStateOf<PendingChoice?>(null) }
    val usage by produceState<com.cansin.app.system.StorageUsage?>(null, refresh) { value = storage.usage() }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    SectionHeader(R.string.section_storage)
    usage?.let { u ->
        Text(stringResource(R.string.storage_usage, Formatter.formatShortFileSize(context, u.totalBytes)))
        Text(
            listOfNotNull(
                pluralStringResource(R.plurals.storage_nights, u.nights, u.nights),
                u.compressed.takeIf { it > 0 }?.let { stringResource(R.string.storage_compressed, it) },
                u.questions.takeIf { it > 0 }?.let { stringResource(R.string.storage_questions, it) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
    }
    OutlinedButton(
        onClick = {
            busy = true
            scope.launch {
                freed = storage.freeUpSpace()
                busy = false
                refresh++
            }
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) { Text(stringResource(R.string.storage_free_up)) }
    freed?.let {
        Text(
            if (it > 0) stringResource(R.string.storage_freed, Formatter.formatShortFileSize(context, it)) else stringResource(R.string.storage_already),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Text(stringResource(R.string.storage_free_note), style = MaterialTheme.typography.bodySmall, color = muted)

    fun choose(count: suspend () -> Int, save: suspend () -> Unit, @StringRes title: Int, @PluralsRes body: Int) {
        scope.launch {
            val n = count()
            if (n == 0) {
                save()
            } else {
                pending = PendingChoice(title, body, n) {
                    save()
                    storage.run()
                }
            }
        }
    }

    Text(stringResource(R.string.setting_keep_history), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
    Column(Modifier.selectableGroup()) {
        KeepHistory.entries.forEach { option ->
            RadioRow(stringResource(option.label), option == settings.keepHistory) {
                if (option != settings.keepHistory) {
                    choose({ storage.wouldDelete(option) }, { onKeepHistory(option) }, R.string.confirm_delete_nights_title, R.plurals.confirm_delete_nights_body)
                }
            }
        }
    }
    Text(stringResource(R.string.setting_compress), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    Column(Modifier.selectableGroup()) {
        CompressAfter.entries.forEach { option ->
            RadioRow(stringResource(option.label), option == settings.compressAfter) {
                if (option != settings.compressAfter) {
                    choose({ storage.wouldCompress(option) }, { onCompressAfter(option) }, R.string.confirm_compress_title, R.plurals.confirm_compress_body)
                }
            }
        }
    }
    Text(stringResource(R.string.compress_note), style = MaterialTheme.typography.bodySmall, color = muted)

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(p.title)) },
            text = { Text(pluralStringResource(p.body, p.count, p.count)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    busy = true
                    scope.launch {
                        p.apply()
                        busy = false
                        refresh++
                    }
                }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = {
                        pending = null
                        onExport(false)
                    }) { Text(stringResource(R.string.confirm_backup_first)) }
                    TextButton(onClick = { pending = null }) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )
    }
}

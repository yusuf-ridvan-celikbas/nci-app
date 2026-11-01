package com.cansin.app.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.cansin.app.domain.QuestionImport
import com.cansin.app.system.QuestionFiles
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.data.CustomQuestion
import com.cansin.app.data.QuestionKind
import com.cansin.app.domain.GameRules

/** Own Truth or Dare items: saved only on this phone (and in your own export). */
@Composable
fun MyQuestionsDialog(viewModel: GameViewModel, own: List<CustomQuestion>, onDismiss: () -> Unit) {
    var kind by rememberSaveable { mutableStateOf(QuestionKind.TRUTH) }
    var text by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    val clean = GameRules.cleanQuestion(text)
    var helping by rememberSaveable { mutableStateOf(false) }
    var report by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.game_import_failed)
    val resultFormat = stringResource(R.string.game_import_result)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val content = QuestionFiles.read(context, uri)
                if (content == null) {
                    report = failed
                } else {
                    viewModel.importOwn(content) { r, added ->
                        report = String.format(resultFormat, added, r.invalid, r.duplicates, r.overLimit + (r.questions.size - added))
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_my_questions_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.game_own_note), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { report = null; picker.launch(CSV_TYPES) }) { Text(stringResource(R.string.game_import)) }
                    TextButton(onClick = { helping = true }) { Text(stringResource(R.string.game_import_help)) }
                }
                report?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuestionKind.entries.forEach { k ->
                        FilterChip(
                            selected = k == kind,
                            onClick = { kind = k },
                            label = { Text(stringResource(if (k == QuestionKind.TRUTH) R.string.game_truth else R.string.game_dare)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(CustomQuestion.MAX_TEXT + 1); error = null },
                    label = { Text(stringResource(R.string.game_own_hint)) },
                    isError = error != null || text.length > CustomQuestion.MAX_TEXT,
                    supportingText = (error ?: R.string.game_own_invalid.takeIf { text.length > CustomQuestion.MAX_TEXT })?.let { { Text(stringResource(it)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    enabled = clean != null,
                    onClick = {
                        viewModel.addOwn(kind, text) { ok -> if (ok) text = "" else error = R.string.game_own_full }
                    },
                ) { Text(stringResource(R.string.action_add)) }
                if (own.isEmpty()) {
                    Text(stringResource(R.string.game_own_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(own, key = { it.id }) { q ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(if (q.kind == QuestionKind.TRUTH) R.string.game_truth else R.string.game_dare),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(q.text, style = MaterialTheme.typography.bodyMedium)
                                }
                                IconButton(onClick = { viewModel.deleteOwn(q.id) }) {
                                    Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_continue)) } },
    )
    if (helping) ImportHelpDialog(onDismiss = { helping = false })
}

/** Some apps label CSV as plain text, Excel CSV or a generic file. */
private val CSV_TYPES = arrayOf(
    "text/csv", "text/comma-separated-values", "application/csv", "text/plain",
    "application/vnd.ms-excel", "application/octet-stream",
)

/** Step-by-step guide plus a ready template (header + one example per type) to open in Excel. */
@Composable
private fun ImportHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val truth = stringResource(R.string.game_truth)
    val dare = stringResource(R.string.game_dare)
    val csv = QuestionImport.template(
        stringResource(R.string.game_csv_type) to stringResource(R.string.game_csv_question),
        listOf(truth to stringResource(R.string.game_csv_example_truth), dare to stringResource(R.string.game_csv_example_dare)),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_import_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.game_import_steps))
                Text(
                    stringResource(R.string.game_import_example, truth, stringResource(R.string.game_csv_example_truth)) + "\n" +
                        stringResource(R.string.game_import_example, dare, stringResource(R.string.game_csv_example_dare)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = {
                    scope.launch {
                        try {
                            context.startActivity(QuestionFiles.templateIntent(context, csv))
                        } catch (_: ActivityNotFoundException) {
                        }
                    }
                }) { Text(stringResource(R.string.game_import_template)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_continue)) } },
    )
}

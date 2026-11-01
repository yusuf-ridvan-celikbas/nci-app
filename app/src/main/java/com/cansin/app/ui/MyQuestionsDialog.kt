package com.cansin.app.ui

import androidx.compose.foundation.layout.Arrangement
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_my_questions_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.game_own_note), style = MaterialTheme.typography.bodySmall)
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
}

package com.cansin.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.data.FriendProfile
import com.cansin.app.data.ImportResult
import com.cansin.app.domain.ResultCard
import com.cansin.app.domain.ResultCards
import java.time.ZoneId

@Composable
fun FriendResultsTab(viewModel: HistoryViewModel) {
    val results by viewModel.friendResults.collectAsState()
    var importing by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<Int?>(null) }
    LazyColumn {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { importing = true; message = null }) { Text(stringResource(R.string.friends_import)) }
                message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary) }
                Text(
                    stringResource(R.string.friends_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (results?.isEmpty() == true) {
                    Text(stringResource(R.string.friends_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
        }
        items(results.orEmpty(), key = { it.id }) { result ->
            val card = result.card ?: return@items
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CardText(card, Modifier.weight(1f))
                IconButton(onClick = { viewModel.deleteFriendResult(result.id) }) {
                    Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete))
                }
            }
            HorizontalDivider()
        }
    }
    if (importing) {
        ImportDialog(
            parse = viewModel::parseCard,
            onImport = { card ->
                importing = false
                viewModel.importCard(card) {
                    message = if (it == ImportResult.ADDED) R.string.friends_imported else R.string.friends_duplicate
                }
            },
            onDismiss = { importing = false },
        )
    }
}

@Composable
private fun CardText(card: ResultCard, modifier: Modifier = Modifier) {
    val day = card.day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    Column(modifier) {
        Text(card.name + " · " + formatDate(day), style = MaterialTheme.typography.bodyLarge)
        Text(
            listOf(
                pluralStringResource(R.plurals.beers_count, card.beers, card.beers),
                formatDuration(card.durationMin * 60_000L),
                stringResource(R.string.water_count, card.water),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The user pastes text themselves (keyboard paste); the app never reads the clipboard. */
@Composable
private fun ImportDialog(parse: (String) -> ResultCard?, onImport: (ResultCard) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val card = if (text.isBlank()) null else parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.friends_import)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_PASTE) },
                    label = { Text(stringResource(R.string.friends_import_hint)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    card != null -> CardText(card)
                    text.isNotBlank() -> Text(stringResource(R.string.friends_import_invalid), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { card?.let(onImport) }, enabled = card != null) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Name prompt + preview, then the system share sheet. Only name and counts leave the phone, by the user's choice. */
@Composable
fun ShareResultDialog(initialName: String?, build: (String) -> ResultCard?, onNameUsed: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initialName.orEmpty()) }
    val card = build(name)
    val messageTemplate = stringResource(R.string.share_message, "%s")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_result)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.share_body), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(FriendProfile.MAX_NAME) },
                    label = { Text(stringResource(R.string.share_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                card?.let { CardText(it) }
            }
        },
        confirmButton = {
            TextButton(enabled = card != null, onClick = {
                val c = card ?: return@TextButton
                onNameUsed(c.name)
                shareText(context, messageTemplate.replace("%s", ResultCards.encode(c)))
                onDismiss()
            }) { Text(stringResource(R.string.action_share)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (_: ActivityNotFoundException) {
    }
}

private const val MAX_PASTE = 5_000

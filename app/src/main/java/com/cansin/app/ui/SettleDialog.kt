package com.cansin.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.data.Side
import com.cansin.app.domain.SettleRules

@Composable
fun SettleDialog(state: NotesState, onDismiss: () -> Unit) {
    val format = rememberAmountFormat()
    val context = LocalContext.current
    var splitShared by rememberSaveable { mutableStateOf(true) }
    var people by rememberSaveable { mutableIntStateOf(SettleRules.MIN_SPLIT) }
    val total = state.totals.total
    val unnamed = stringResource(R.string.settle_unnamed)
    val lines = mutableListOf<String>()

    if (state.versus) {
        val totals = state.totals
        val (red, blue) = if (splitShared) SettleRules.splitShared(totals) else totals.red to totals.blue
        lines += stringResource(R.string.tab_side_total, sideName(state.session, Side.RED), format(red))
        lines += stringResource(R.string.tab_side_total, sideName(state.session, Side.BLUE), format(blue))
        if (!splitShared) lines += stringResource(R.string.tab_side_total, sideName(state.session, null), format(totals.shared))
    } else {
        SettleRules.byPerson(state.notes).forEach { lines += stringResource(R.string.tab_side_total, it.person ?: unnamed, format(it.minor)) }
    }
    val totalLine = stringResource(R.string.tab_total, format(total))
    val peopleText = pluralStringResource(R.plurals.settle_people, people, people)
    val eachLine = peopleText + ": " + stringResource(R.string.settle_each, format(SettleRules.eachPays(total, people)))
    val title = stringResource(R.string.settle_title)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settle_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (lines.isEmpty()) Text(stringResource(R.string.settle_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                lines.forEach { Text(it) }
                Text(totalLine, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                if (state.versus) {
                    SwitchRow(R.string.settle_split_shared, splitShared) { splitShared = it }
                } else {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text(stringResource(R.string.settle_split_evenly), style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { people-- }, enabled = people > SettleRules.MIN_SPLIT) { Text(stringResource(R.string.sign_minus)) }
                        Text(
                            peopleText,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        OutlinedButton(onClick = { people++ }, enabled = people < SettleRules.MAX_SPLIT) { Text(stringResource(R.string.sign_plus)) }
                    }
                    Text(eachLine, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = total > 0,
                onClick = {
                    val text = (listOf(title) + lines + totalLine + listOfNotNull(eachLine.takeIf { !state.versus }))
                        .joinToString("\n")
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    try {
                        context.startActivity(Intent.createChooser(send, null))
                    } catch (_: ActivityNotFoundException) {
                    }
                },
            ) { Text(stringResource(R.string.settle_share)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

package com.cansin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.domain.PictureRules
import com.cansin.app.domain.PictureChange
import com.cansin.app.data.FriendProfile
import com.cansin.app.data.GroupSeat
import com.cansin.app.data.Side
import com.cansin.app.domain.GroupRules
import com.cansin.app.domain.GroupSetup

private const val DEFAULT_SEATS = 4

private val namesSaver = listSaver<SnapshotStateList<String>, String>({ it.toList() }, { it.toMutableStateList() })

private fun List<String>.toMutableStateList() = mutableStateListOf<String>().also { it.addAll(this) }

/** Start or edit a "together" session. Existing seats can be renamed but not removed. */
@Composable
fun GroupSetupDialog(
    seats: List<GroupSeat>,
    me: Side?,
    editing: Boolean,
    endsCurrentSession: Boolean,
    sessionId: Long?,
    onSave: (GroupSetup, Map<Side, PictureChange>) -> Unit,
    onDismiss: () -> Unit,
) {
    val ordered = GroupRules.ordered(seats)
    val minCount = if (editing) ordered.size.coerceAtLeast(GroupRules.MIN_SEATS) else GroupRules.MIN_SEATS
    var count by rememberSaveable { mutableIntStateOf(if (editing) minCount else DEFAULT_SEATS) }
    val names = rememberSaveable(saver = namesSaver) {
        List(GroupRules.MAX_SEATS) { i -> ordered.getOrNull(i)?.name.orEmpty() }.toMutableStateList()
    }
    var meIndex by rememberSaveable { mutableIntStateOf(GroupRules.SIDES.indexOf(me)) }
    val pictures = rememberSaveable(saver = namesSaver) {
        List(GroupRules.MAX_SEATS) { PictureChange.KEEP.name }.toMutableStateList()
    }
    val sides = GroupRules.sides(count)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (editing) R.string.group_edit else R.string.group_start)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.group_intro), style = MaterialTheme.typography.bodySmall)
                if (endsCurrentSession) Text(stringResource(R.string.group_ends_session), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.group_seats), style = MaterialTheme.typography.titleSmall)
                FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (GroupRules.MIN_SEATS..GroupRules.MAX_SEATS).forEach { n ->
                        FilterChip(
                            selected = n == count,
                            enabled = n >= minCount,
                            onClick = { count = n; if (meIndex >= n) meIndex = -1 },
                            label = { Text(n.toString()) },
                            modifier = Modifier.semantics { role = Role.RadioButton },
                        )
                    }
                }
                sides.forEachIndexed { i, side ->
                    val placeholder = stringResource(R.string.group_seat_default, i + 1)
                    OutlinedTextField(
                        value = names[i],
                        onValueChange = { names[i] = it.take(FriendProfile.MAX_NAME) },
                        label = { Text(placeholder) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = sideColor(side)?.let { (c, _) ->
                            androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = c, unfocusedBorderColor = c)
                        } ?: androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
                    )
                    SidePicture(
                        side,
                        sessionId?.let { PictureRules.side(it, side) },
                        PictureChange.valueOf(pictures[i]),
                        names[i].ifBlank { placeholder },
                    ) { pictures[i] = it.name }
                }
                Text(stringResource(R.string.versus_me), style = MaterialTheme.typography.titleSmall)
                FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (sides.indices.toList() + -1).forEach { i ->
                        val colors = sideColor(sides.getOrNull(i))
                        FilterChip(
                            selected = i == meIndex,
                            onClick = { meIndex = i },
                            label = {
                                Text(
                                    if (i < 0) stringResource(R.string.versus_me_none)
                                    else names[i].trim().ifEmpty { stringResource(R.string.group_seat_default, i + 1) },
                                    maxLines = 1,
                                )
                            },
                            colors = if (colors == null) FilterChipDefaults.filterChipColors() else FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.first,
                                selectedLabelColor = colors.second,
                            ),
                            modifier = Modifier.semantics { role = Role.RadioButton },
                        )
                    }
                }
                Text(stringResource(R.string.versus_me_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val changes = GroupRules.SIDES.take(GroupRules.MAX_SEATS).mapIndexed { i, side -> side to PictureChange.valueOf(pictures[i]) }.toMap()
                onSave(GroupSetup(names.take(count).map { it.ifBlank { null } }, sides.getOrNull(meIndex)), changes)
            }) { Text(stringResource(if (editing) R.string.action_save else R.string.action_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Counter's "people" button: classic two-sided Versus or the 2–10 seat "together" mode. */
@Composable
fun ModeChooserDialog(onVersus: () -> Unit, onGroup: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mode_title)) },
        text = {
            Column {
                OutlinedButton(onClick = onVersus, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mode_versus)) }
                OutlinedButton(onClick = onGroup, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mode_group)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

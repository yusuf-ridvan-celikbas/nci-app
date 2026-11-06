package com.cansin.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.cansin.app.domain.PictureRules
import com.cansin.app.domain.PictureChange
import com.cansin.app.data.FriendProfile
import com.cansin.app.data.Session
import com.cansin.app.data.Side

@Composable
fun VersusSetupDialog(
    session: Session?,
    friends: List<FriendProfile>,
    endsCurrentSession: Boolean,
    onSave: (red: SideDraft, blue: SideDraft, me: Side?) -> Unit,
    onDismiss: () -> Unit,
) {
    var redName by rememberSaveable { mutableStateOf(session?.redName.orEmpty()) }
    var redFriend by rememberSaveable { mutableStateOf(session?.redFriendId) }
    var redSave by rememberSaveable { mutableStateOf(false) }
    var blueName by rememberSaveable { mutableStateOf(session?.blueName.orEmpty()) }
    var blueFriend by rememberSaveable { mutableStateOf(session?.blueFriendId) }
    var blueSave by rememberSaveable { mutableStateOf(false) }
    var me by rememberSaveable { mutableStateOf(session?.meSide) }
    var redPicture by rememberSaveable { mutableStateOf(PictureChange.KEEP) }
    var bluePicture by rememberSaveable { mutableStateOf(PictureChange.KEEP) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.versus_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (endsCurrentSession) {
                    Text(stringResource(R.string.versus_ends_session), style = MaterialTheme.typography.bodySmall)
                }
                SideFields(Side.RED, redName, redFriend, redSave, friends, { name, id -> redName = name; redFriend = id }, { redSave = it })
                SidePicture(Side.RED, PictureRules.keyFor(session?.id, Side.RED, redFriend), redPicture, redName) { redPicture = it }
                SideFields(Side.BLUE, blueName, blueFriend, blueSave, friends, { name, id -> blueName = name; blueFriend = id }, { blueSave = it })
                SidePicture(Side.BLUE, PictureRules.keyFor(session?.id, Side.BLUE, blueFriend), bluePicture, blueName) { bluePicture = it }
                Text(stringResource(R.string.versus_me), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    listOf(Side.RED, Side.BLUE, null).forEach { side ->
                        val label = if (side == null) stringResource(R.string.versus_me_none) else sideName(null, side)
                        RadioRow(label, me == side) { me = side }
                    }
                }
                Text(
                    stringResource(R.string.versus_me_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(SideDraft(redName, redFriend, redSave, redPicture), SideDraft(blueName, blueFriend, blueSave, bluePicture), me)
            }) { Text(stringResource(if (session == null) R.string.action_start else R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** One side's +1 picture in the dialog: a new pick waits as a draft until Save. */
@Composable
internal fun SidePicture(side: Side, savedKey: String?, change: PictureChange, name: String, onChange: (PictureChange) -> Unit) {
    val store = LocalPictures.current ?: return
    val scope = rememberCoroutineScope()
    val shown = when (change) {
        PictureChange.NEW -> rememberPicture(PictureRules.draft(side))
        PictureChange.REMOVE -> null
        PictureChange.KEEP -> rememberPicture(savedKey)
    }
    val pick = rememberPicturePicker { bitmap ->
        store.save(PictureRules.draft(side), bitmap)
        onChange(PictureChange.NEW)
    }
    val label = stringResource(R.string.picture_for, name.trim().ifEmpty { sideName(null, side) })
    PictureRow(shown, label, onPick = pick, onRemove = {
        scope.launch { store.delete(PictureRules.draft(side)) }
        onChange(PictureChange.REMOVE)
    })
}

@Composable
private fun SideFields(
    side: Side,
    name: String,
    friendId: Long?,
    save: Boolean,
    friends: List<FriendProfile>,
    onName: (String, Long?) -> Unit,
    onSave: (Boolean) -> Unit,
) {
    OutlinedTextField(
        value = name,
        onValueChange = { text ->
            val value = text.take(FriendProfile.MAX_NAME)
            onName(value, friendId?.takeIf { id -> friends.any { it.id == id && it.name == value.trim() } })
        },
        label = { Text(sideName(null, side)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (friends.isNotEmpty()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            friends.forEach { friend ->
                FilterChip(
                    selected = friend.id == friendId,
                    onClick = { onName(friend.name, friend.id) },
                    label = { Text(friend.name, maxLines = 1) },
                )
            }
        }
    }
    val known = friends.any { it.name.equals(name.trim(), ignoreCase = true) }
    if (friendId == null && name.isNotBlank() && !known) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(save, role = Role.Checkbox, onValueChange = onSave).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = save, onCheckedChange = null)
            Text(stringResource(R.string.versus_save_friend))
        }
    }
}

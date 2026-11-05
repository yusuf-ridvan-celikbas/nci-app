package com.cansin.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import kotlinx.coroutines.launch

/** Game tab tap: a sheet slides up with Soy tu Tentación, Versus and Together. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSheet(onTruthOrDare: () -> Unit, onVersus: () -> Unit, onTogether: () -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun pick(action: () -> Unit) {
        scope.launch { sheet.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetRow(R.drawable.ic_game, { GameTitle() }, R.string.game_pick_tod_desc) { pick(onTruthOrDare) }
            SheetRow(R.drawable.ic_versus, { Text(stringResource(R.string.mode_versus)) }, R.string.game_pick_versus_desc) { pick(onVersus) }
            SheetRow(R.drawable.ic_versus, { Text(stringResource(R.string.mode_group)) }, R.string.game_pick_group_desc) { pick(onTogether) }
        }
    }
}

@Composable
private fun SheetRow(@DrawableRes icon: Int, title: @Composable () -> Unit, @StringRes description: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = title,
        supportingContent = { Text(stringResource(description)) },
        leadingContent = { Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

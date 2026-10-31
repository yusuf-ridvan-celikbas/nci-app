package com.cansin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.data.Session
import com.cansin.app.data.Side
import com.cansin.app.ui.theme.VersusColors

@Composable
fun sideName(session: Session?, side: Side?): String = when (side) {
    Side.RED -> session?.redName ?: stringResource(R.string.side_red)
    Side.BLUE -> session?.blueName ?: stringResource(R.string.side_blue)
    else -> stringResource(R.string.side_shared)
}

fun sideColor(side: Side?): Pair<Color, Color>? = when (side) {
    Side.RED -> VersusColors.red to VersusColors.onRed
    Side.BLUE -> VersusColors.blue to VersusColors.onBlue
    else -> null
}

@Composable
fun SideChips(session: Session?, selected: Side?, onSelect: (Side?) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Side.RED, Side.BLUE, null).forEach { side ->
            val colors = sideColor(side)
            FilterChip(
                selected = side == selected,
                onClick = { onSelect(side) },
                label = { Text(sideName(session, side), maxLines = 1) },
                colors = if (colors == null) FilterChipDefaults.filterChipColors() else FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.first,
                    selectedLabelColor = colors.second,
                ),
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
}

package com.cansin.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.cansin.app.R

/** P25: root tabs. "Tonight" shows the counter, or the open Versus/Together screen. */
enum class RootTab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    TONIGHT(R.string.nav_tonight, R.drawable.ic_shortcut_beer),
    TAB(R.string.nav_tab, R.drawable.ic_receipt),
    GAME(R.string.nav_game, R.drawable.ic_game),
    HISTORY(R.string.nav_history, R.drawable.ic_history),
    SETTINGS(R.string.nav_settings, R.drawable.ic_settings),
}

@Composable
fun AppNavBar(selected: RootTab, tabs: List<RootTab>, onSelect: (RootTab) -> Unit) {
    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tab.icon), null) },
                label = { Text(stringResource(tab.label), maxLines = 1) },
            )
        }
    }
}

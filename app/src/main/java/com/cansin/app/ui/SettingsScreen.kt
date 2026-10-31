package com.cansin.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.data.FriendProfile
import com.cansin.app.domain.BeerSize
import com.cansin.app.domain.BodyProfile
import com.cansin.app.prefs.Accent
import com.cansin.app.prefs.AppName
import com.cansin.app.prefs.Language
import com.cansin.app.prefs.Settings
import com.cansin.app.prefs.ThemeMode

class SettingsActions(
    val onBack: () -> Unit,
    val onAppName: (AppName) -> Unit,
    val onLanguage: (Language) -> Unit,
    val onThemeMode: (ThemeMode) -> Unit,
    val onAccent: (Accent) -> Unit,
    val onRemindersEnabled: (Boolean) -> Unit,
    val onReminderInterval: (Int) -> Unit,
    val onReminderSilent: (Boolean) -> Unit,
    val onOpenNotificationSettings: () -> Unit,
    val onBeerSize: (BeerSize) -> Unit,
    val onProfile: (BodyProfile?) -> Unit,
    val onDeleteFriend: (Long) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    language: Language,
    dynamicAvailable: Boolean,
    notificationAccess: NotificationAccess,
    friends: List<FriendProfile>,
    actions: SettingsActions,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionHeader(R.string.section_general)
            ChoiceGroup(R.string.setting_app_name, AppName.entries, settings.appName, { it.label }, actions.onAppName)
            Text(
                stringResource(R.string.name_change_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChoiceGroup(R.string.setting_language, Language.entries, language, { it.label }, actions.onLanguage)
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            SectionHeader(R.string.section_appearance)
            ChoiceGroup(R.string.setting_theme_mode, ThemeMode.entries, settings.themeMode, { it.label }, actions.onThemeMode)
            val accents = Accent.entries.filter { it != Accent.DYNAMIC || dynamicAvailable }
            ChoiceGroup(R.string.setting_color, accents, settings.accent, { it.label }, actions.onAccent)
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            BeerSection(settings.beerSize, actions.onBeerSize)
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            ProfileSection(settings.profile, actions.onProfile)
            if (friends.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                FriendsSection(friends, actions.onDeleteFriend)
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            ReminderSection(settings, notificationAccess, actions)
        }
    }
}

@Composable
private fun BeerSection(size: BeerSize, onChange: (BeerSize) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    SectionHeader(R.string.section_beer)
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { editing = true }.padding(vertical = 8.dp),
    ) {
        Text(stringResource(R.string.setting_default_beer), style = MaterialTheme.typography.titleSmall)
        Text(beerSizeLabel(size), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
        stringResource(R.string.pick_size_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (editing) {
        BeerSizeDialog(
            title = R.string.setting_default_beer,
            confirm = R.string.action_save,
            initial = size,
            onConfirm = {
                onChange(it)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun ProfileSection(profile: BodyProfile?, onChange: (BodyProfile?) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    SectionHeader(R.string.section_estimate)
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { editing = true }.padding(vertical = 8.dp),
    ) {
        Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.titleSmall)
        Text(
            if (profile == null) stringResource(R.string.profile_not_set) else stringResource(profile.license.label),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (editing) {
        ProfileDialog(
            initial = profile,
            onSave = {
                onChange(it)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun FriendsSection(friends: List<FriendProfile>, onDelete: (Long) -> Unit) {
    SectionHeader(R.string.section_friends)
    friends.forEach { friend ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(friend.name, Modifier.weight(1f))
            TextButton(onClick = { onDelete(friend.id) }) {
                Text(stringResource(R.string.action_delete))
            }
        }
    }
}

@Composable
internal fun SectionHeader(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp).semantics { heading() },
    )
}

@Composable
private fun <T> ChoiceGroup(
    @StringRes title: Int,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    Column(Modifier.selectableGroup()) {
        options.forEach { option -> RadioRow(stringResource(label(option)), option == selected) { onSelect(option) } }
    }
}

@Composable
internal fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}

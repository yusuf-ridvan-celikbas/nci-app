package com.cansin.app.ui

import androidx.annotation.StringRes
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cansin.app.R
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
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    language: Language,
    dynamicAvailable: Boolean,
    notificationAccess: NotificationAccess,
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
            ReminderSection(settings, notificationAccess, actions)
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
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(label(option)))
            }
        }
    }
}

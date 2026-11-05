package com.cansin.app.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.data.FriendProfile
import com.cansin.app.domain.BeerSize
import com.cansin.app.domain.BodyProfile
import com.cansin.app.domain.Currency
import com.cansin.app.domain.WaterRules
import com.cansin.app.prefs.Accent
import com.cansin.app.prefs.Language
import com.cansin.app.prefs.Settings
import com.cansin.app.prefs.ThemeMode
import com.cansin.app.system.AppLock
import kotlin.math.roundToInt

class SettingsActions(
    val onBack: () -> Unit,
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
    val onWaterEnabled: (Boolean) -> Unit,
    val onWaterEvery: (Int) -> Unit,
    val onTimerEnabled: (Boolean) -> Unit,
    val onGroupLayout: (com.cansin.app.prefs.GroupLayout) -> Unit,
    val onCurrency: (Currency) -> Unit,
    val onCareEnabled: (Boolean) -> Unit,
    val onCareLateHour: (Int) -> Unit,
    val onHomePlace: (String?) -> Unit,
    val onLimits: (Int?, Long?) -> Unit,
    val lock: AppLock,
    val onPinHidesWidget: (Boolean) -> Unit,
    val onPinChanged: () -> Unit,
    val onDefaultDrink: (com.cansin.app.data.DrinkKind) -> Unit,
    val onExport: (Boolean) -> Unit,
    val onReadBackup: suspend (android.net.Uri) -> com.cansin.app.domain.RestoreResult,
    val onRestore: suspend (com.cansin.app.domain.RestoreData) -> Unit,
    val onDeleteAll: () -> Unit,
    val storage: com.cansin.app.system.StorageMaintenance,
    val onKeepHistory: suspend (com.cansin.app.domain.KeepHistory) -> Unit,
    val onCompressAfter: suspend (com.cansin.app.domain.CompressAfter) -> Unit,
)

/** Settings is a short list of groups; each opens its own page. */
enum class SettingsPage(@StringRes val title: Int, @StringRes val summary: Int, @DrawableRes val icon: Int) {
    GENERAL(R.string.settings_general, R.string.settings_general_summary, R.drawable.ic_settings),
    DRINKS(R.string.settings_drinks, R.string.settings_drinks_summary, R.drawable.ic_shortcut_beer),
    HEALTH(R.string.settings_health, R.string.settings_health_summary, R.drawable.ic_shortcut_water),
    FRIENDS(R.string.settings_friends, R.string.settings_friends_summary, R.drawable.ic_versus),
    PRIVACY(R.string.settings_privacy, R.string.settings_privacy_summary, R.drawable.ic_lock),
}

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
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BackHandler(page != null) { page = null }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(page?.title ?: R.string.settings)) },
                navigationIcon = {
                    if (page != null) {
                        IconButton(onClick = { page = null }) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val current = page
        if (current == null) {
            Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                SettingsPage.entries.forEach { p ->
                    ListItem(
                        headlineContent = { Text(stringResource(p.title)) },
                        supportingContent = { Text(stringResource(p.summary)) },
                        leadingContent = { Icon(painterResource(p.icon), null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable(role = Role.Button) { page = p },
                    )
                }
            }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            when (current) {
                SettingsPage.GENERAL -> {
                    ChoiceGroup(R.string.setting_language, Language.entries, language, { it.label }, actions.onLanguage)
                    ChoiceGroup(R.string.setting_currency, Currency.entries, settings.currency, ::currencyLabel, actions.onCurrency)
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    SectionHeader(R.string.section_appearance)
                    ChoiceGroup(R.string.setting_theme_mode, ThemeMode.entries, settings.themeMode, { it.label }, actions.onThemeMode)
                    val accents = Accent.entries.filter { it != Accent.DYNAMIC || dynamicAvailable }
                    ChoiceGroup(R.string.setting_color, accents, settings.accent, { it.label }, actions.onAccent)
                }
                SettingsPage.DRINKS -> {
                    BeerSection(settings.beerSize, actions.onBeerSize, settings.defaultDrink, actions.onDefaultDrink)
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    SectionHeader(R.string.setting_timer)
                    SwitchRow(R.string.setting_timer, settings.timerEnabled, actions.onTimerEnabled)
                    Text(
                        stringResource(R.string.setting_timer_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SettingsPage.HEALTH -> {
                    WaterSection(settings, actions)
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    CareSection(settings, actions)
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    ProfileSection(settings.profile, actions.onProfile)
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    ReminderSection(settings, notificationAccess, actions)
                }
                SettingsPage.FRIENDS -> {
                    SectionHeader(R.string.group_title)
                    ChoiceGroup(
                        R.string.setting_group_layout,
                        com.cansin.app.prefs.GroupLayout.entries,
                        settings.groupLayout,
                        { it.label },
                        actions.onGroupLayout,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    FriendsSection(friends, actions.onDeleteFriend)
                }
                SettingsPage.PRIVACY -> {
                    PinSection(settings, actions.lock, actions.onPinHidesWidget, actions.onPinChanged)
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    DataSection(actions.onExport, actions.onReadBackup, actions.onRestore, actions.onDeleteAll)
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    StorageSection(settings, actions.storage, actions.onKeepHistory, actions.onCompressAfter, actions.onExport)
                }
            }
        }
    }
}

@Composable
private fun BeerSection(
    size: BeerSize,
    onChange: (BeerSize) -> Unit,
    drink: com.cansin.app.data.DrinkKind,
    onDrink: (com.cansin.app.data.DrinkKind) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var pickingDrink by rememberSaveable { mutableStateOf(false) }
    SectionHeader(R.string.section_beer)
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { pickingDrink = true }.padding(vertical = 8.dp),
    ) {
        Text(stringResource(R.string.setting_default_drink), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(drinkName(drink)), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (pickingDrink) {
        DefaultDrinkDialog(drink, size, { onDrink(it); pickingDrink = false }, { pickingDrink = false })
    }
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
private fun WaterSection(settings: Settings, actions: SettingsActions) {
    SectionHeader(R.string.section_water)
    SwitchRow(R.string.setting_water, settings.waterEnabled, actions.onWaterEnabled)
    if (settings.waterEnabled) {
        var every by remember(settings.waterEvery) { mutableFloatStateOf(settings.waterEvery.toFloat()) }
        Text(pluralStringResource(R.plurals.water_every, every.roundToInt(), every.roundToInt()))
        Slider(
            value = every,
            onValueChange = { every = it },
            onValueChangeFinished = { actions.onWaterEvery(every.roundToInt()) },
            valueRange = WaterRules.MIN_EVERY.toFloat()..WaterRules.MAX_EVERY.toFloat(),
            steps = WaterRules.MAX_EVERY - WaterRules.MIN_EVERY - 1,
        )
    }
}

@Composable
private fun FriendsSection(friends: List<FriendProfile>, onDelete: (Long) -> Unit) {
    SectionHeader(R.string.section_friends)
    if (friends.isEmpty()) {
        Text(stringResource(R.string.settings_no_friends), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    friends.forEach { friend ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(friend.name, Modifier.weight(1f))
            TextButton(onClick = { onDelete(friend.id) }) {
                Text(stringResource(R.string.action_delete))
            }
        }
    }
}

private fun currencyLabel(currency: Currency): Int = when (currency) {
    Currency.NONE -> R.string.currency_none
    Currency.TRY -> R.string.currency_try
    Currency.EUR -> R.string.currency_eur
    Currency.USD -> R.string.currency_usd
    Currency.GBP -> R.string.currency_gbp
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

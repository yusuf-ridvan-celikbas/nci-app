package com.cansin.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.cansin.app.prefs.Language
import com.cansin.app.prefs.Settings
import com.cansin.app.prefs.SettingsStore
import com.cansin.app.prefs.ThemeMode
import com.cansin.app.system.AppLocale
import com.cansin.app.system.LauncherAlias
import com.cansin.app.ui.AgeGateScreen
import com.cansin.app.ui.CounterScreen
import com.cansin.app.ui.CounterViewModel
import com.cansin.app.ui.NotesScreen
import com.cansin.app.ui.NotesViewModel
import com.cansin.app.ui.NotificationAccess
import com.cansin.app.ui.ProfileDialog
import com.cansin.app.ui.PromilleCard
import com.cansin.app.ui.PromilleScreen
import com.cansin.app.ui.PromilleTicker
import com.cansin.app.ui.PromilleViewModel
import com.cansin.app.ui.SettingsActions
import com.cansin.app.ui.SettingsScreen
import com.cansin.app.ui.theme.CansinTheme
import com.cansin.app.ui.theme.dynamicColorAvailable
import com.cansin.app.widget.CounterWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private enum class Screen { COUNTER, TAB, SETTINGS, PROMILLE }

class MainActivity : ComponentActivity() {
    private val app get() = application as CansinApp
    private val store: SettingsStore get() = app.settingsStore
    private val counter: CounterViewModel by viewModels { CounterViewModel.factory(app.sessionRepository) }
    private val notes: NotesViewModel by viewModels { NotesViewModel.factory(app.sessionRepository) }
    private val promille: PromilleViewModel by viewModels { PromilleViewModel.factory(app.sessionRepository, app.settingsStore) }
    private var resumed by mutableStateOf(false)
    private var notificationAccess by mutableStateOf(NotificationAccess(permissionGranted = false, enabled = false))

    override fun attachBaseContext(newBase: Context) {
        val language = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val store = (newBase.applicationContext as CansinApp).settingsStore
            runBlocking { store.settings.first().language }
        } else {
            Language.SYSTEM
        }
        super.attachBaseContext(AppLocale.wrap(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by store.settings.collectAsState(initial = null)
            settings?.let { App(it) }
        }
    }

    override fun onStart() {
        super.onStart()
        counter.refresh()
    }

    override fun onResume() {
        super.onResume()
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val enabled = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        notificationAccess = NotificationAccess(granted, enabled)
        resumed = true
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    @Composable
    private fun App(settings: Settings) {
        val dark = when (settings.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        LaunchedEffect(dark) {
            val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
            enableEdgeToEdge(style, style)
        }
        var screen by rememberSaveable { mutableStateOf(Screen.COUNTER) }
        BackHandler(screen != Screen.COUNTER) { screen = Screen.COUNTER }

        CansinTheme(dark, settings.accent) {
            when {
                !settings.ageConfirmed -> AgeGateScreen(
                    onConfirm = { update { confirmAge() } },
                    onDecline = { finish() },
                )
                screen == Screen.SETTINGS -> SettingsScreen(
                    settings = settings,
                    language = AppLocale.current(this, settings.language),
                    dynamicAvailable = dynamicColorAvailable,
                    notificationAccess = notificationAccess,
                    actions = settingsActions(onBack = { screen = Screen.COUNTER }),
                )
                screen == Screen.TAB -> NotesScreen(notes, onBack = { screen = Screen.COUNTER })
                screen == Screen.PROMILLE -> {
                    PromilleTicker(promille, resumed)
                    PromilleScreen(promille, onBack = { screen = Screen.COUNTER })
                }
                else -> CounterScreen(
                    viewModel = counter,
                    title = stringResource(settings.appName.label),
                    defaultSize = settings.beerSize,
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenTab = { screen = Screen.TAB },
                    promilleCard = { PromilleSlot(onOpen = { screen = Screen.PROMILLE }) },
                )
            }
        }
    }

    @Composable
    private fun PromilleSlot(onOpen: () -> Unit) {
        val state by promille.state.collectAsState()
        var settingUp by rememberSaveable { mutableStateOf(false) }
        PromilleTicker(promille, resumed)
        PromilleCard(state, onOpen, onSetUp = { settingUp = true }, onDismissPrompt = { promille.dismissPrompt() })
        if (settingUp) {
            ProfileDialog(
                initial = null,
                onSave = {
                    promille.saveProfile(it)
                    settingUp = false
                },
                onDismiss = { settingUp = false },
            )
        }
    }

    private fun settingsActions(onBack: () -> Unit) = SettingsActions(
        onBack = onBack,
        onAppName = { name ->
            update { setAppName(name) }
            LauncherAlias.apply(applicationContext, name)
        },
        onLanguage = { language ->
            lifecycleScope.launch {
                store.setLanguage(language)
                CounterWidget.refreshAll(app)
                AppLocale.apply(this@MainActivity, language)
            }
        },
        onThemeMode = { mode -> update { setThemeMode(mode) } },
        onAccent = { accent -> update { setAccent(accent) } },
        onRemindersEnabled = { enabled -> lifecycleScope.launch { app.reminders.setEnabled(enabled) } },
        onReminderInterval = { minutes -> update { setReminderInterval(minutes) } },
        onReminderSilent = { silent -> update { setReminderSilent(silent) } },
        onOpenNotificationSettings = {
            startActivity(
                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName),
            )
        },
        onBeerSize = { size -> update { setBeerSize(size) } },
        onProfile = { profile -> update { setProfile(profile) } },
    )

    private fun update(block: suspend SettingsStore.() -> Unit) {
        lifecycleScope.launch {
            store.block()
            CounterWidget.refreshAll(app)
        }
    }
}

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
import androidx.compose.runtime.CompositionLocalProvider
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
import com.cansin.app.ui.HistoryScreen
import com.cansin.app.ui.HistoryViewModel
import com.cansin.app.ui.SessionDetailScreen
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
import com.cansin.app.ui.VersusScreen
import com.cansin.app.ui.VersusSetupDialog
import com.cansin.app.ui.VersusViewModel
import com.cansin.app.ui.UndoEffect
import com.cansin.app.ui.WaterBar
import com.cansin.app.ui.TimerBar
import com.cansin.app.domain.TimerRules
import com.cansin.app.domain.TimerState
import com.cansin.app.ui.WaterViewModel
import androidx.compose.material3.SnackbarHostState
import com.cansin.app.ui.theme.CansinTheme
import com.cansin.app.ui.LocalCurrency
import com.cansin.app.ui.CareCard
import com.cansin.app.ui.CareTicker
import com.cansin.app.ui.CareViewModel
import com.cansin.app.domain.QuickAction
import com.cansin.app.ui.theme.dynamicColorAvailable
import com.cansin.app.widget.CounterWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private enum class Screen { COUNTER, TAB, SETTINGS, PROMILLE, HISTORY, DETAIL }

class MainActivity : ComponentActivity() {
    private val app get() = application as CansinApp
    private val store: SettingsStore get() = app.settingsStore
    private val counter: CounterViewModel by viewModels { CounterViewModel.factory(app.sessionRepository) }
    private val notes: NotesViewModel by viewModels { NotesViewModel.factory(app.sessionRepository) }
    private val promille: PromilleViewModel by viewModels { PromilleViewModel.factory(app.sessionRepository, app.settingsStore) }
    private val versus: VersusViewModel by viewModels { VersusViewModel.factory(app.sessionRepository, app.friendRepository) }
    private val water: WaterViewModel by viewModels { WaterViewModel.factory(app.sessionRepository, app.settingsStore) }
    private val care: CareViewModel by viewModels { CareViewModel.factory(app.sessionRepository, app.settingsStore) }
    private val history: HistoryViewModel by viewModels { HistoryViewModel.factory(app.historyRepository, app.settingsStore) }
    private var resumed by mutableStateOf(false)
    private var openTabRequest by mutableStateOf(false)
    private var quickAction by mutableStateOf<QuickAction?>(null)
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
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by store.settings.collectAsState(initial = null)
            settings?.let { App(it) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_TAB, false) == true) openTabRequest = true
        QuickAction.parse(intent?.getStringExtra(EXTRA_SHORTCUT))?.let { quickAction = it }
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
        var editingSides by rememberSaveable { mutableStateOf(false) }
        val versusState by versus.state.collectAsState()
        val friends by versus.friends.collectAsState()
        var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
        LaunchedEffect(detailId) { history.select(detailId) }
        LaunchedEffect(quickAction, settings.ageConfirmed) {
            val action = quickAction ?: return@LaunchedEffect
            if (!settings.ageConfirmed) return@LaunchedEffect
            quickAction = null
            when (action) {
                QuickAction.BEER -> { screen = Screen.COUNTER; counter.add() }
                QuickAction.WATER -> { screen = Screen.COUNTER; water.add() }
                QuickAction.TAB -> screen = Screen.TAB
            }
        }
        LaunchedEffect(openTabRequest) {
            if (openTabRequest) {
                screen = Screen.TAB
                openTabRequest = false
            }
        }
        BackHandler(screen != Screen.COUNTER) { screen = if (screen == Screen.DETAIL) Screen.HISTORY else Screen.COUNTER }

        CansinTheme(dark, settings.accent) { CompositionLocalProvider(LocalCurrency provides settings.currency) {
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
                    friends = friends,
                    actions = settingsActions(onBack = { screen = Screen.COUNTER }),
                )
                screen == Screen.TAB -> NotesScreen(notes, onBack = { screen = Screen.COUNTER })
                screen == Screen.HISTORY -> HistoryScreen(
                    history,
                    onBack = { screen = Screen.COUNTER },
                    onOpen = { id ->
                        detailId = id
                        screen = Screen.DETAIL
                    },
                )
                screen == Screen.DETAIL -> SessionDetailScreen(history, onBack = { screen = Screen.HISTORY })
                screen == Screen.PROMILLE -> {
                    PromilleTicker(promille, resumed)
                    PromilleScreen(promille, onBack = { screen = Screen.COUNTER })
                }
                versusState.session != null -> VersusScreen(
                    viewModel = versus,
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenTab = { screen = Screen.TAB },
                    onEditSides = { editingSides = true },
                    onOpenHistory = { screen = Screen.HISTORY },
                    promilleCard = { CareSlot(); PromilleSlot(onOpen = { screen = Screen.PROMILLE }) },
                    waterBar = { WaterSlot(it) },
                )
                else -> CounterScreen(
                    viewModel = counter,
                    title = stringResource(settings.appName.label),
                    defaultSize = settings.beerSize,
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenTab = { screen = Screen.TAB },
                    onStartVersus = { editingSides = true },
                    onOpenHistory = { screen = Screen.HISTORY },
                    promilleCard = { CareSlot(); PromilleSlot(onOpen = { screen = Screen.PROMILLE }) },
                    waterBar = { WaterSlot(it) },
                )
            }
            if (editingSides && settings.ageConfirmed) {
                val session = versusState.session
                VersusSetupDialog(
                    session = session,
                    friends = friends,
                    endsCurrentSession = session == null && counter.state.collectAsState().value.sessionActive,
                    onSave = { red, blue, me ->
                        versus.save(red, blue, me, start = session == null)
                        editingSides = false
                    },
                    onDismiss = { editingSides = false },
                )
            }
        } }
    }

    @Composable
    private fun CareSlot() {
        val state by care.state.collectAsState()
        CareTicker(care, resumed)
        CareCard(state, care::dismiss)
    }

    @Composable
    private fun WaterSlot(snackbar: SnackbarHostState) {
        val state by water.state.collectAsState()
        val settings by store.settings.collectAsState(initial = null)
        val timer by store.timerState.collectAsState(initial = TimerState())
        val promilleState by promille.state.collectAsState()
        val myLatestBeer = promilleState.beers.lastOrNull()
        if (settings?.timerEnabled == true) {
            val running = timer.running && timer.sessionId == myLatestBeer?.sessionId
            TimerBar(
                startedAt = timer.startedAt.takeIf { running },
                canStart = TimerRules.canStart(true, timer, myLatestBeer),
                resumed = resumed,
                onStart = { app.appScope.launch { app.drinkTimer.start(); CounterWidget.refreshAll(app) } },
                onStop = { app.appScope.launch { app.drinkTimer.stop(); CounterWidget.refreshAll(app) } },
            )
        }
        UndoEffect(water.waterAdded, snackbar, water::undo, R.string.water_added)
        WaterBar(state, onAdd = water::add, onDismiss = water::dismiss)
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
        onDeleteFriend = { id -> versus.deleteFriend(id) },
        onWaterEnabled = { on -> update { setWaterEnabled(on) } },
        onWaterEvery = { n -> update { setWaterEvery(n) } },
        onTimerEnabled = { on -> update { setTimerEnabled(on) } },
        onCurrency = { c -> update { setCurrency(c) } },
        onCareEnabled = { on -> update { setCareEnabled(on) } },
        onCareLateHour = { h -> update { setCareLateHour(h) } },
        onHomePlace = { home -> update { setHomePlace(home) } },
        onLimits = { beers, budget -> update { setLimits(beers, budget) } },
        onExport = { includeProfile -> lifecycleScope.launch { startActivity(app.dataManager.export(includeProfile)) } },
        onDeleteAll = { lifecycleScope.launch { app.dataManager.deleteAll() } },
    )

    private fun update(block: suspend SettingsStore.() -> Unit) {
        lifecycleScope.launch {
            store.block()
            CounterWidget.refreshAll(app)
        }
    }

    companion object {
        const val EXTRA_OPEN_TAB = "com.cansin.app.OPEN_TAB"
        const val EXTRA_SHORTCUT = "com.cansin.app.SHORTCUT"
    }
}

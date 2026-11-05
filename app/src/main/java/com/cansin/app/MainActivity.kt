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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.cansin.app.ui.AppNavBar
import com.cansin.app.ui.RootTab
import com.cansin.app.ui.GameMenu
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
import com.cansin.app.ui.GroupViewModel
import com.cansin.app.ui.GameViewModel
import com.cansin.app.ui.GameScreen
import com.cansin.app.system.ScreenPin
import com.cansin.app.domain.PinScreenRules
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.cansin.app.ui.GroupScreen
import com.cansin.app.ui.GroupSetupDialog
import com.cansin.app.ui.ModeChooserDialog
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
import com.cansin.app.domain.PinRules
import com.cansin.app.ui.LockScreen
import com.cansin.app.ui.theme.dynamicColorAvailable
import com.cansin.app.widget.CounterWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private enum class Screen { COUNTER, TAB, SETTINGS, PROMILLE, HISTORY, DETAIL, GAME }

/** Root screens shown with the bottom bar (P25); PROMILLE and DETAIL open on top without it. */
private val NAV_TABS = listOf(RootTab.TONIGHT, RootTab.TAB, RootTab.GAME, RootTab.HISTORY, RootTab.SETTINGS)

private fun Screen.tab(): RootTab? = when (this) {
    Screen.COUNTER -> RootTab.TONIGHT
    Screen.TAB -> RootTab.TAB
    Screen.GAME -> RootTab.GAME
    Screen.HISTORY -> RootTab.HISTORY
    Screen.SETTINGS -> RootTab.SETTINGS
    Screen.PROMILLE, Screen.DETAIL -> null
}

private fun RootTab.screen(): Screen = when (this) {
    RootTab.TONIGHT -> Screen.COUNTER
    RootTab.TAB -> Screen.TAB
    RootTab.GAME -> Screen.GAME
    RootTab.HISTORY -> Screen.HISTORY
    RootTab.SETTINGS -> Screen.SETTINGS
}

class MainActivity : ComponentActivity() {
    private val app get() = application as CansinApp
    private val store: SettingsStore get() = app.settingsStore
    private val counter: CounterViewModel by viewModels { CounterViewModel.factory(app.sessionRepository) }
    private val notes: NotesViewModel by viewModels { NotesViewModel.factory(app.sessionRepository) }
    private val promille: PromilleViewModel by viewModels { PromilleViewModel.factory(app.sessionRepository, app.settingsStore) }
    private val versus: VersusViewModel by viewModels { VersusViewModel.factory(app.sessionRepository, app.friendRepository) }
    private val group: GroupViewModel by viewModels {
        GroupViewModel.factory(app.sessionRepository, app.settingsStore, app.drinkTimer, app.groupTimers) { CounterWidget.refreshAll(app) }
    }
    private val game: GameViewModel by viewModels { GameViewModel.factory(app.gameRepository, app.settingsStore) }
    private val water: WaterViewModel by viewModels { WaterViewModel.factory(app.sessionRepository, app.settingsStore) }
    private val care: CareViewModel by viewModels { CareViewModel.factory(app.sessionRepository, app.settingsStore) }
    private val history: HistoryViewModel by viewModels { HistoryViewModel.factory(app.historyRepository, app.settingsStore) }
    private var resumed by mutableStateOf(false)
    private var openTabRequest by mutableStateOf(false)
    private var quickAction by mutableStateOf<QuickAction?>(null)
    private var locked by mutableStateOf(true)
    private var pinned by mutableStateOf(false)
    private var pinInfo by mutableStateOf(false)
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
        val lock = app.appLock
        val pinSet = runBlocking { store.settings.first().pinSet }
        if (PinRules.shouldLock(pinSet, lock.stoppedAt, System.currentTimeMillis())) lock.unlocked = false
        locked = pinSet && !lock.unlocked
        counter.refresh()
    }

    override fun onStop() {
        app.appLock.stoppedAt = System.currentTimeMillis()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val enabled = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        notificationAccess = NotificationAccess(granted, enabled)
        resumed = true
        pinned = ScreenPin.isPinned(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        pinned = ScreenPin.isPinned(this)
    }

    private fun togglePin(infoSeen: Boolean) {
        when {
            pinned -> { ScreenPin.unpin(this); pinned = ScreenPin.isPinned(this) }
            !infoSeen -> pinInfo = true
            else -> ScreenPin.pin(this)
        }
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
        var choosingMode by rememberSaveable { mutableStateOf(false) }
        var editingGroup by rememberSaveable { mutableStateOf(false) }
        var gameSheet by rememberSaveable { mutableStateOf(false) }
        val versusState by versus.state.collectAsState()
        val groupState by group.state.collectAsState()
        val friends by versus.friends.collectAsState()
        var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
        LaunchedEffect(detailId) { history.select(detailId) }
        val showLock = settings.pinSet && locked
        LaunchedEffect(settings.pinSet) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!settings.pinSet)
            if (!settings.pinSet) locked = false
        }
        LaunchedEffect(quickAction, settings.ageConfirmed, showLock) {
            val action = quickAction ?: return@LaunchedEffect
            if (!settings.ageConfirmed || showLock) return@LaunchedEffect
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

        val recentBrands by app.sessionRepository.recentBrands.collectAsState(initial = emptyList())
        CansinTheme(dark, settings.accent) { CompositionLocalProvider(LocalCurrency provides settings.currency, com.cansin.app.ui.LocalRecentBrands provides recentBrands) {
            val tab = screen.tab()
            val showBar = settings.ageConfirmed && !showLock && tab != null
            Scaffold(
                bottomBar = {
                    if (showBar && tab != null) AppNavBar(tab, NAV_TABS) {
                        if (it == RootTab.GAME) gameSheet = !gameSheet else { gameSheet = false; screen = it.screen() }
                    }
                },
                contentWindowInsets = WindowInsets(0),
            ) { inner ->
            Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)) {
            Box(Modifier.fillMaxSize()) {
            when {
                showLock -> LockScreen(
                    title = stringResource(R.string.name_salgam),
                    lock = app.appLock,
                    onUnlocked = { locked = false },
                    onDeleteAll = { lifecycleScope.launch { app.dataManager.deleteAll() } },
                )
                !settings.ageConfirmed -> AgeGateScreen(
                    onConfirm = { update { confirmAge() } },
                    onDecline = { finish() },
                )
                screen == Screen.SETTINGS -> SettingsScreen(
                    settings = settings,
                    language = AppLocale.current(this@MainActivity, settings.language),
                    dynamicAvailable = dynamicColorAvailable,
                    notificationAccess = notificationAccess,
                    friends = friends,
                    actions = settingsActions(onBack = { screen = Screen.COUNTER }),
                )
                screen == Screen.GAME -> GameScreen(
                    game,
                    session = groupState.session ?: versusState.session,
                    seats = groupState.seats,
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
                groupState.session != null -> GroupScreen(
                    viewModel = group,
                    game = game,
                    onEditSeats = { editingGroup = true },
                    pinned = pinned,
                    onTogglePin = { togglePin(settings.pinInfoSeen) },
                    resumed = resumed,
                    defaultDrink = com.cansin.app.domain.Drinks.default(settings.defaultDrink, settings.beerSize, settings.defaultBrand),
                    beerSize = settings.beerSize,
                    layout = settings.groupLayout,
                    onLayout = { layout -> update { setGroupLayout(layout) } },
                    promilleCard = { CareSlot(); PromilleSlot(onOpen = { screen = Screen.PROMILLE }) },
                )
                versusState.session != null -> VersusScreen(
                    viewModel = versus,
                    game = game,
                    onEditSides = { editingSides = true },
                    pinned = pinned,
                    onTogglePin = { togglePin(settings.pinInfoSeen) },
                    promilleCard = { CareSlot(); PromilleSlot(onOpen = { screen = Screen.PROMILLE }) },
                    waterBar = { WaterSlot(it) },
                )
                else -> CounterScreen(
                    viewModel = counter,
                    title = stringResource(R.string.name_salgam),
                    defaultDrink = com.cansin.app.domain.Drinks.default(settings.defaultDrink, settings.beerSize, settings.defaultBrand),
                    beerSize = settings.beerSize,
                    onStartVersus = { choosingMode = true },
                    promilleCard = { CareSlot(); PromilleSlot(onOpen = { screen = Screen.PROMILLE }) },
                    waterBar = { WaterSlot(it) },
                )
            }
            }
            GameMenu(
                open = gameSheet && settings.ageConfirmed && !showLock,
                // An already open Versus/Together is shown on Tonight; otherwise its setup opens (then Tonight).
                onTruthOrDare = { screen = Screen.GAME },
                onVersus = { if (versusState.session != null) screen = Screen.COUNTER else editingSides = true },
                onTogether = { if (groupState.session != null) screen = Screen.COUNTER else editingGroup = true },
                onDismiss = { gameSheet = false },
            )
            }
            }
            val shared = versusState.session != null || groupState.session != null
            LaunchedEffect(shared, versusState.loaded, groupState.loaded) {
                if (versusState.loaded && groupState.loaded && !shared) game.sessionClosed()
            }
            LaunchedEffect(shared, pinned, versusState.loaded, groupState.loaded) {
                if (versusState.loaded && groupState.loaded && PinScreenRules.shouldAutoUnpin(pinned, shared)) {
                    ScreenPin.unpin(this@MainActivity)
                    pinned = ScreenPin.isPinned(this@MainActivity)
                }
            }
            if (pinInfo) {
                AlertDialog(
                    onDismissRequest = { pinInfo = false },
                    title = { Text(stringResource(R.string.pin_info_title)) },
                    text = { Text(stringResource(R.string.pin_info_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            pinInfo = false
                            update { markPinInfoSeen() }
                            ScreenPin.pin(this@MainActivity)
                        }) { Text(stringResource(R.string.pin_screen)) }
                    },
                    dismissButton = { TextButton(onClick = { pinInfo = false }) { Text(stringResource(R.string.action_cancel)) } },
                )
            }
            if (choosingMode && settings.ageConfirmed) {
                ModeChooserDialog(
                    onVersus = { choosingMode = false; editingSides = true },
                    onGroup = { choosingMode = false; editingGroup = true },
                    onDismiss = { choosingMode = false },
                )
            }
            if (editingGroup && settings.ageConfirmed) {
                val session = groupState.session
                GroupSetupDialog(
                    seats = groupState.seats,
                    me = session?.meSide,
                    editing = session != null,
                    endsCurrentSession = session == null && counter.state.collectAsState().value.sessionActive,
                    onSave = { setup ->
                        group.save(setup, start = session == null)
                        editingGroup = false
                        screen = Screen.COUNTER
                    },
                    onDismiss = { editingGroup = false },
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
                        screen = Screen.COUNTER
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
        WaterBar(state, onAdd = water::add, onRemove = water::removeLatest, onDismiss = water::dismiss)
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
        onGroupLayout = { layout -> update { setGroupLayout(layout) } },
        onCurrency = { c -> update { setCurrency(c) } },
        onDefaultDrink = { k -> update { setDefaultDrink(k) } },
        onCareEnabled = { on -> update { setCareEnabled(on) } },
        onCareLateHour = { h -> update { setCareLateHour(h) } },
        onHomePlace = { home -> update { setHomePlace(home) } },
        onLimits = { beers, budget -> update { setLimits(beers, budget) } },
        lock = app.appLock,
        onPinHidesWidget = { on -> update { setPinHidesWidget(on) } },
        onPinChanged = { lifecycleScope.launch { CounterWidget.refreshAll(app) } },
        onExport = { includeProfile -> lifecycleScope.launch { startActivity(app.dataManager.export(includeProfile)) } },
        onReadBackup = { uri -> app.dataManager.readBackup(uri) },
        onRestore = { data -> app.dataManager.restore(data) },
        onDeleteAll = { lifecycleScope.launch { app.dataManager.deleteAll() } },
        storage = app.storage,
        onKeepHistory = { app.settingsStore.setKeepHistory(it) },
        onCompressAfter = { app.settingsStore.setCompressAfter(it) },
        onPrice = { slot, minor -> update { setPrice(slot, minor) } },
        onDefaultBrand = { brand -> update { setDefaultBrand(brand) } },
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

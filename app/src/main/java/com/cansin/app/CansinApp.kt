package com.cansin.app

import com.cansin.app.system.AppLock
import android.app.Application
import com.cansin.app.domain.Drinks
import com.cansin.app.data.AppDatabase
import com.cansin.app.data.BeerEvent
import com.cansin.app.data.FriendRepository
import com.cansin.app.data.HistoryRepository
import com.cansin.app.system.DataManager
import com.cansin.app.data.SessionListener
import com.cansin.app.data.SessionRepository
import com.cansin.app.prefs.SettingsStore
import com.cansin.app.reminder.Reminders
import com.cansin.app.timer.DrinkTimer
import com.cansin.app.widget.CounterWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CansinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // P27: quiet daily upkeep (retention, compression, compaction); never blocks start-up.
        appScope.launch {
            runCatching { storage.scrubBrands() }
            runCatching { settingsStore.removeBrandPrefs() }
            runCatching { storage.runIfDue() }
            runCatching { pictures.clearDrafts() }
        }
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database by lazy { AppDatabase.build(this) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val friendRepository: FriendRepository by lazy { FriendRepository(database.friendDao()) }
    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(database.sessionDao(), database.beerDao(), database.noteDao(), database.waterDao(), database.friendResultDao(), database.groupSeatDao())
    }
    val dataManager: DataManager by lazy {
        DataManager(this, database, settingsStore, { reminders.cancel() }, { CounterWidget.refreshAll(this) }, { storage.compactNow() }, { pictures.deleteAll() })
    }
    val storage: com.cansin.app.system.StorageMaintenance by lazy {
        com.cansin.app.system.StorageMaintenance(this, database, settingsStore, { block -> sessionRepository.exclusive { block() } })
    }
    val appLock: AppLock by lazy { AppLock(settingsStore) }
    val pictures: com.cansin.app.system.PictureStore by lazy { com.cansin.app.system.PictureStore(this) }
    val drinkTimer: DrinkTimer by lazy { DrinkTimer(settingsStore, { sessionRepository }) }
    val gameRepository: com.cansin.app.data.GameRepository by lazy { com.cansin.app.data.GameRepository(database.customQuestionDao()) }
    val groupTimers: com.cansin.app.timer.GroupTimers by lazy { com.cansin.app.timer.GroupTimers(settingsStore, { sessionRepository }) }
    val reminders: Reminders by lazy { Reminders(this, settingsStore, { sessionRepository }) }

    val sessionRepository: SessionRepository by lazy {
        SessionRepository(
            database.sessionDao(),
            database.beerDao(),
            database.noteDao(),
            database.waterDao(),
            listener = object : SessionListener {
                override suspend fun onChanged() = CounterWidget.refreshAll(this@CansinApp)
                override suspend fun onBeerAdded(event: BeerEvent, mine: Boolean) {
                    if (mine) {
                        reminders.onBeerAdded(event.sessionId)
                        drinkTimer.autoStart(event)
                    }
                }
                override suspend fun onBeerRemoved(beerId: Long) = drinkTimer.clearIfBeer(beerId)
                override suspend fun onSessionClosed(sessionId: Long) {
                    reminders.onSessionClosed(sessionId)
                    drinkTimer.clearIf(sessionId)
                    groupTimers.clear()
                }
            },
            defaultDrink = { settingsStore.settings.first().let { Drinks.default(it.defaultDrink, it.beerSize, it.defaultServing) } },
            seats = database.groupSeatDao(),
            price = { kind, sizeMl -> com.cansin.app.domain.PriceRules.lookup(settingsStore.settings.first().prices, kind, sizeMl) },
            waterPrice = { ml -> com.cansin.app.domain.PriceRules.water(settingsStore.settings.first().prices, ml) },
            defaultWaterMl = { settingsStore.settings.first().waterSizeMl },
        )
    }
}

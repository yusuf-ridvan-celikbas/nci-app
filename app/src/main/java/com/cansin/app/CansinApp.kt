package com.cansin.app

import android.app.Application
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

class CansinApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database by lazy { AppDatabase.build(this) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val friendRepository: FriendRepository by lazy { FriendRepository(database.friendDao()) }
    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(database.sessionDao(), database.beerDao(), database.noteDao(), database.waterDao(), database.friendResultDao())
    }
    val dataManager: DataManager by lazy {
        DataManager(this, database, settingsStore, { reminders.cancel() }, { CounterWidget.refreshAll(this) })
    }
    val drinkTimer: DrinkTimer by lazy { DrinkTimer(settingsStore, { sessionRepository }) }
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
                    if (mine) reminders.onBeerAdded(event.sessionId)
                }
                override suspend fun onSessionClosed(sessionId: Long) {
                    reminders.onSessionClosed(sessionId)
                    drinkTimer.clearIf(sessionId)
                }
            },
            defaultSize = { settingsStore.settings.first().beerSize },
        )
    }
}

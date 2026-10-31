package com.cansin.app

import android.app.Application
import com.cansin.app.data.AppDatabase
import com.cansin.app.data.BeerEvent
import com.cansin.app.data.SessionListener
import com.cansin.app.data.SessionRepository
import com.cansin.app.prefs.SettingsStore
import com.cansin.app.reminder.Reminders
import com.cansin.app.widget.CounterWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CansinApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database by lazy { AppDatabase.build(this) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val reminders: Reminders by lazy { Reminders(this, settingsStore, { sessionRepository }) }

    val sessionRepository: SessionRepository by lazy {
        SessionRepository(
            database.sessionDao(),
            database.beerDao(),
            database.noteDao(),
            listener = object : SessionListener {
                override suspend fun onChanged() = CounterWidget.refreshAll(this@CansinApp)
                override suspend fun onBeerAdded(event: BeerEvent) = reminders.onBeerAdded(event.sessionId)
                override suspend fun onSessionClosed(sessionId: Long) = reminders.onSessionClosed(sessionId)
            },
        )
    }
}

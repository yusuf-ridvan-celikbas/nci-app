package com.cansin.app

import android.app.Application
import com.cansin.app.data.AppDatabase
import com.cansin.app.data.SessionRepository
import com.cansin.app.prefs.SettingsStore
import com.cansin.app.widget.CounterWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CansinApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database by lazy { AppDatabase.build(this) }
    val sessionRepository by lazy {
        SessionRepository(database.sessionDao(), database.beerDao(), onChange = { CounterWidget.refreshAll(this) })
    }
    val settingsStore by lazy { SettingsStore(this) }
}

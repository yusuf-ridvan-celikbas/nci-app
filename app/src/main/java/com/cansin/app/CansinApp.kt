package com.cansin.app

import android.app.Application
import com.cansin.app.data.AppDatabase
import com.cansin.app.data.SessionRepository
import com.cansin.app.prefs.SettingsStore

class CansinApp : Application() {
    private val database by lazy { AppDatabase.build(this) }
    val sessionRepository by lazy { SessionRepository(database.sessionDao(), database.beerDao()) }
    val settingsStore by lazy { SettingsStore(this) }
}

package com.cansin.app.prefs

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cansin.app.reminder.ReminderPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private val AGE_CONFIRMED = booleanPreferencesKey("age_confirmed")
private val APP_NAME = stringPreferencesKey("app_name")
private val LANGUAGE = stringPreferencesKey("language")
private val THEME_MODE = stringPreferencesKey("theme_mode")
private val ACCENT = stringPreferencesKey("accent")
private val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
private val REMINDER_INTERVAL = intPreferencesKey("reminder_interval_min")
private val REMINDER_SILENT = booleanPreferencesKey("reminder_silent")
private val REMINDER_NEXT_AT = longPreferencesKey("reminder_next_at")
private val REMINDER_SESSION = longPreferencesKey("reminder_session")
private val REMINDER_STOPPED_SESSION = longPreferencesKey("reminder_stopped_session")

class SettingsStore(private val context: Context) {
    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            ageConfirmed = p[AGE_CONFIRMED] == true,
            appName = enumOr(p[APP_NAME], AppName.SOY_TU_TENTACION),
            language = enumOr(p[LANGUAGE], Language.SYSTEM),
            themeMode = enumOr(p[THEME_MODE], ThemeMode.SYSTEM),
            accent = enumOr(p[ACCENT], Accent.AMBER),
            remindersEnabled = p[REMINDERS_ENABLED] == true,
            reminderIntervalMin = ReminderPolicy.clampInterval(p[REMINDER_INTERVAL] ?: Settings.DEFAULT_REMINDER_INTERVAL_MIN),
            reminderSilent = p[REMINDER_SILENT] == true,
        )
    }

    val reminderState: Flow<ReminderState> = context.dataStore.data.map { p ->
        ReminderState(p[REMINDER_NEXT_AT], p[REMINDER_SESSION], p[REMINDER_STOPPED_SESSION])
    }

    suspend fun confirmAge() = edit { it[AGE_CONFIRMED] = true }
    suspend fun setAppName(value: AppName) = edit { it[APP_NAME] = value.name }
    suspend fun setLanguage(value: Language) = edit { it[LANGUAGE] = value.name }
    suspend fun setThemeMode(value: ThemeMode) = edit { it[THEME_MODE] = value.name }
    suspend fun setAccent(value: Accent) = edit { it[ACCENT] = value.name }
    suspend fun setRemindersEnabled(value: Boolean) = edit { it[REMINDERS_ENABLED] = value }
    suspend fun setReminderInterval(minutes: Int) = edit { it[REMINDER_INTERVAL] = ReminderPolicy.clampInterval(minutes) }
    suspend fun setReminderSilent(value: Boolean) = edit { it[REMINDER_SILENT] = value }

    suspend fun setScheduledReminder(at: Long?, sessionId: Long?) = edit {
        it.putOrRemove(REMINDER_NEXT_AT, at)
        it.putOrRemove(REMINDER_SESSION, sessionId)
    }

    suspend fun setStoppedSession(sessionId: Long?) = edit { it.putOrRemove(REMINDER_STOPPED_SESSION, sessionId) }

    private fun <T : Any> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else set(key, value)
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}

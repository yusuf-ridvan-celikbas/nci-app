package com.cansin.app.prefs

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cansin.app.domain.BeerRules
import com.cansin.app.domain.BeerSize
import com.cansin.app.domain.BodyProfile
import com.cansin.app.domain.License
import com.cansin.app.domain.Sex
import com.cansin.app.domain.WaterRules
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
private val DEFAULT_SIZE_ML = intPreferencesKey("default_size_ml")
private val DEFAULT_ABV = doublePreferencesKey("default_abv")
private val PROFILE_SEX = stringPreferencesKey("profile_sex")
private val PROFILE_AGE = intPreferencesKey("profile_age")
private val PROFILE_HEIGHT = intPreferencesKey("profile_height_cm")
private val PROFILE_WEIGHT = intPreferencesKey("profile_weight_kg")
private val PROFILE_LICENSE = stringPreferencesKey("profile_license")
private val PROFILE_PROMPT_DISMISSED = booleanPreferencesKey("profile_prompt_dismissed")
private val WATER_ENABLED = booleanPreferencesKey("water_enabled")
private val WATER_EVERY = intPreferencesKey("water_every")
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
            beerSize = BeerRules.clamp(
                BeerSize(p[DEFAULT_SIZE_ML] ?: BeerSize.DEFAULT.sizeMl, p[DEFAULT_ABV] ?: BeerSize.DEFAULT.abv),
            ),
            profile = readProfile(p),
            profilePromptDismissed = p[PROFILE_PROMPT_DISMISSED] == true,
            waterEnabled = p[WATER_ENABLED] ?: true,
            waterEvery = WaterRules.clamp(p[WATER_EVERY] ?: WaterRules.DEFAULT_EVERY),
        )
    }

    private fun readProfile(p: Preferences): BodyProfile? {
        val profile = BodyProfile(
            sex = enumOr(p[PROFILE_SEX], Sex.UNSPECIFIED),
            age = p[PROFILE_AGE] ?: return null,
            heightCm = p[PROFILE_HEIGHT] ?: return null,
            weightKg = p[PROFILE_WEIGHT] ?: return null,
            license = enumOr(p[PROFILE_LICENSE], License.NONE),
        )
        return profile.takeIf { it.isValid }
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

    suspend fun setBeerSize(value: BeerSize) = edit {
        val clamped = BeerRules.clamp(value)
        it[DEFAULT_SIZE_ML] = clamped.sizeMl
        it[DEFAULT_ABV] = clamped.abv
    }

    suspend fun setProfile(value: BodyProfile?) = edit {
        if (value == null || !value.isValid) {
            it.remove(PROFILE_SEX)
            it.remove(PROFILE_AGE)
            it.remove(PROFILE_HEIGHT)
            it.remove(PROFILE_WEIGHT)
            it.remove(PROFILE_LICENSE)
        } else {
            it[PROFILE_SEX] = value.sex.name
            it[PROFILE_AGE] = value.age
            it[PROFILE_HEIGHT] = value.heightCm
            it[PROFILE_WEIGHT] = value.weightKg
            it[PROFILE_LICENSE] = value.license.name
        }
    }

    suspend fun setWaterEnabled(value: Boolean) = edit { it[WATER_ENABLED] = value }
    suspend fun setWaterEvery(value: Int) = edit { it[WATER_EVERY] = WaterRules.clamp(value) }

    suspend fun dismissProfilePrompt() = edit { it[PROFILE_PROMPT_DISMISSED] = true }

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

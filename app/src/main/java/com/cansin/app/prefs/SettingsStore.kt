package com.cansin.app.prefs

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

class SettingsStore(private val context: Context) {
    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            ageConfirmed = p[AGE_CONFIRMED] == true,
            appName = enumOr(p[APP_NAME], AppName.SOY_TU_TENTACION),
            language = enumOr(p[LANGUAGE], Language.SYSTEM),
            themeMode = enumOr(p[THEME_MODE], ThemeMode.SYSTEM),
            accent = enumOr(p[ACCENT], Accent.AMBER),
        )
    }

    suspend fun confirmAge() = edit { it[AGE_CONFIRMED] = true }
    suspend fun setAppName(value: AppName) = edit { it[APP_NAME] = value.name }
    suspend fun setLanguage(value: Language) = edit { it[LANGUAGE] = value.name }
    suspend fun setThemeMode(value: ThemeMode) = edit { it[THEME_MODE] = value.name }
    suspend fun setAccent(value: Accent) = edit { it[ACCENT] = value.name }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}

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
import com.cansin.app.data.DrinkKind
import com.cansin.app.domain.BeerRules
import com.cansin.app.domain.Drinks
import com.cansin.app.domain.ResultCards
import com.cansin.app.domain.BeerSize
import com.cansin.app.domain.BodyProfile
import com.cansin.app.domain.CareRules
import com.cansin.app.domain.Currency
import com.cansin.app.domain.License
import com.cansin.app.domain.Sex
import com.cansin.app.domain.TimerState
import com.cansin.app.domain.WaterRules
import com.cansin.app.reminder.ReminderPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
private val PIN_INFO_SEEN = booleanPreferencesKey("pin_info_seen")
private val GROUP_TIMERS = stringPreferencesKey("group_timers")
private val WATER_ENABLED = booleanPreferencesKey("water_enabled")
private val WATER_EVERY = intPreferencesKey("water_every")
private val TIMER_ENABLED = booleanPreferencesKey("timer_enabled")
private val SHARE_NAME = stringPreferencesKey("share_name")
private val CURRENCY = stringPreferencesKey("currency")
private val CARE_ENABLED = booleanPreferencesKey("care_enabled")
private val CARE_LATE_HOUR = intPreferencesKey("care_late_hour")
private val HOME_PLACE = stringPreferencesKey("home_place")
private val BEER_LIMIT = intPreferencesKey("beer_limit")
private val DEFAULT_DRINK = stringPreferencesKey("default_drink")
private val BUDGET_MINOR = longPreferencesKey("budget_minor")
private val PIN_HASH = stringPreferencesKey("pin_hash")
private val PIN_HIDES_WIDGET = booleanPreferencesKey("pin_hides_widget")
private val PIN_FAILS = intPreferencesKey("pin_fails")
private val PIN_LAST_FAIL = longPreferencesKey("pin_last_fail_at")
private val TIMER_BEER = longPreferencesKey("timer_beer_id")
private val TIMER_SESSION = longPreferencesKey("timer_session_id")
private val TIMER_STARTED = longPreferencesKey("timer_started_at")
private const val WIDGET_PAGE_PREFIX = "widget_page_"
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
            defaultDrink = enumOr(p[DEFAULT_DRINK], DrinkKind.BEER).takeIf { it in Drinks.DEFAULT_KINDS } ?: DrinkKind.BEER,
            profile = readProfile(p),
            profilePromptDismissed = p[PROFILE_PROMPT_DISMISSED] == true,
            pinInfoSeen = p[PIN_INFO_SEEN] == true,
            waterEnabled = p[WATER_ENABLED] ?: true,
            waterEvery = WaterRules.clamp(p[WATER_EVERY] ?: WaterRules.DEFAULT_EVERY),
            timerEnabled = p[TIMER_ENABLED] ?: true,
            shareName = ResultCards.cleanName(p[SHARE_NAME]),
            currency = enumOr(p[CURRENCY], Currency.NONE),
            careEnabled = p[CARE_ENABLED] ?: true,
            careLateHour = CareRules.clampLateHour(p[CARE_LATE_HOUR] ?: CareRules.DEFAULT_LATE_HOUR),
            homePlace = CareRules.cleanHome(p[HOME_PLACE]),
            beerLimit = CareRules.cleanBeerLimit(p[BEER_LIMIT]),
            budgetMinor = CareRules.cleanBudget(p[BUDGET_MINOR]),
            pinSet = p[PIN_HASH] != null,
            pinHidesWidget = p[PIN_HIDES_WIDGET] ?: true,
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

    /** Encoded seat timers (see SeatTimeRules), only while a Together session is open. */
    val groupTimers: Flow<String?> = context.dataStore.data.map { it[GROUP_TIMERS] }

    suspend fun setGroupTimers(raw: String?) = edit { it.putOrRemove(GROUP_TIMERS, raw) }

    val timerState: Flow<TimerState> = context.dataStore.data.map { p ->
        TimerState(p[TIMER_BEER], p[TIMER_SESSION], p[TIMER_STARTED])
    }

    suspend fun setTimer(state: TimerState) = edit {
        it.putOrRemove(TIMER_BEER, state.beerId)
        it.putOrRemove(TIMER_SESSION, state.sessionId)
        it.putOrRemove(TIMER_STARTED, state.startedAt)
    }

    suspend fun setTimerEnabled(value: Boolean) = edit {
        it[TIMER_ENABLED] = value
        if (!value) {
            it.remove(TIMER_BEER)
            it.remove(TIMER_SESSION)
            it.remove(TIMER_STARTED)
        }
    }

    suspend fun widgetPage(id: Int): Int = context.dataStore.data.first()[intPreferencesKey(WIDGET_PAGE_PREFIX + id)] ?: 0

    suspend fun setWidgetPage(id: Int, page: Int) = edit { it[intPreferencesKey(WIDGET_PAGE_PREFIX + id)] = page }

    suspend fun removeWidgetPages(ids: IntArray) = edit { p -> ids.forEach { p.remove(intPreferencesKey(WIDGET_PAGE_PREFIX + it)) } }

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

    suspend fun clearUserData() = edit { p ->
        val keep = setOf(AGE_CONFIRMED, APP_NAME, LANGUAGE, THEME_MODE, ACCENT)
        p.asMap().keys.filterNot { it in keep }.forEach { p.remove(it) }
    }

    suspend fun setCareEnabled(value: Boolean) = edit { it[CARE_ENABLED] = value }
    suspend fun setCareLateHour(value: Int) = edit { it[CARE_LATE_HOUR] = CareRules.clampLateHour(value) }
    suspend fun setHomePlace(value: String?) = edit { it.putOrRemove(HOME_PLACE, CareRules.cleanHome(value)) }

    suspend fun pinHash(): String? = context.dataStore.data.first()[PIN_HASH]

    suspend fun pinAttempts(): PinAttempts = context.dataStore.data.first().let { PinAttempts(it[PIN_FAILS] ?: 0, it[PIN_LAST_FAIL]) }

    /** New hash (or null to remove) always resets the wrong-try counter. */
    suspend fun setPinHash(hash: String?) = edit {
        it.putOrRemove(PIN_HASH, hash)
        it.remove(PIN_FAILS)
        it.remove(PIN_LAST_FAIL)
    }

    suspend fun recordPinFail(at: Long) = edit {
        it[PIN_FAILS] = (it[PIN_FAILS] ?: 0) + 1
        it[PIN_LAST_FAIL] = at
    }

    suspend fun resetPinFails() = edit {
        it.remove(PIN_FAILS)
        it.remove(PIN_LAST_FAIL)
    }

    suspend fun setPinHidesWidget(value: Boolean) = edit { it[PIN_HIDES_WIDGET] = value }

    suspend fun setLimits(beers: Int?, budgetMinor: Long?) = edit {
        it.putOrRemove(BEER_LIMIT, CareRules.cleanBeerLimit(beers))
        it.putOrRemove(BUDGET_MINOR, CareRules.cleanBudget(budgetMinor))
    }

    suspend fun setCurrency(value: Currency) = edit { it[CURRENCY] = value.name }

    suspend fun setDefaultDrink(value: DrinkKind) = edit { it[DEFAULT_DRINK] = value.name }

    suspend fun setShareName(value: String?) = edit { it.putOrRemove(SHARE_NAME, ResultCards.cleanName(value)) }

    suspend fun dismissProfilePrompt() = edit { it[PROFILE_PROMPT_DISMISSED] = true }

    suspend fun markPinInfoSeen() = edit { it[PIN_INFO_SEEN] = true }

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

package com.cansin.app.prefs

import androidx.annotation.StringRes
import com.cansin.app.R
import com.cansin.app.domain.BeerSize
import com.cansin.app.domain.BodyProfile
import com.cansin.app.domain.CareRules
import com.cansin.app.domain.Currency
import com.cansin.app.domain.WaterRules

enum class Language(@StringRes val label: Int, val tag: String?) {
    SYSTEM(R.string.language_system, null),
    TURKISH(R.string.language_tr, "tr"),
    ENGLISH(R.string.language_en, "en");

    companion object {
        fun fromTags(tags: String): Language {
            val first = tags.substringBefore(',').substringBefore('-').lowercase()
            return entries.firstOrNull { it.tag != null && it.tag == first } ?: SYSTEM
        }
    }
}

enum class ThemeMode(@StringRes val label: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
}

enum class Accent(@StringRes val label: Int) {
    AMBER(R.string.color_amber),
    RED(R.string.color_red),
    BLUE(R.string.color_blue),
    YELLOW(R.string.color_yellow),
    GREEN(R.string.color_green),
    PURPLE(R.string.color_purple),
    DYNAMIC(R.string.color_dynamic),
}

/** P23 Together layout: detailed full-width rows (default) or the original 2-column grid. */
enum class GroupLayout(@StringRes val label: Int) {
    ROWS(R.string.group_layout_rows),
    GRID(R.string.group_layout_grid),
}

data class Settings(
    val ageConfirmed: Boolean = false,
    val language: Language = Language.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.AMBER,
    val remindersEnabled: Boolean = false,
    val reminderIntervalMin: Int = DEFAULT_REMINDER_INTERVAL_MIN,
    val reminderSilent: Boolean = false,
    val beerSize: BeerSize = BeerSize.DEFAULT,
    val defaultDrink: com.cansin.app.data.DrinkKind = com.cansin.app.data.DrinkKind.BEER,
    val profile: BodyProfile? = null,
    val profilePromptDismissed: Boolean = false,
    val pinInfoSeen: Boolean = false,
    /** Soy tu Tentación question sources; at least one is always on. */
    val gameBuiltIn: Boolean = true,
    val gameOwn: Boolean = true,
    val waterEnabled: Boolean = true,
    val waterEvery: Int = WaterRules.DEFAULT_EVERY,
    /** P33: glass size water +1 adds. */
    val waterSizeMl: Int = WaterRules.DEFAULT_ML,
    val timerEnabled: Boolean = true,
    /** P32: +1 (when the drink arrives) starts its timer. */
    val timerAuto: Boolean = true,
    val groupLayout: GroupLayout = GroupLayout.ROWS,
    val keepHistory: com.cansin.app.domain.KeepHistory = com.cansin.app.domain.KeepHistory.FOREVER,
    val compressAfter: com.cansin.app.domain.CompressAfter = com.cansin.app.domain.CompressAfter.NEVER,
    /** P28: price per drink kind (beer per size) in minor units; drinks with a price go on the Tab. */
    val prices: Map<com.cansin.app.domain.PriceSlot, Long> = emptyMap(),
    /** P29: optional brand the big +1 uses for beer. */
    val defaultBrand: String? = null,
    /** P35: brands removed from the brand menu. */
    val hiddenBrands: Set<String> = emptySet(),
    /** P34: draft or bottle for beer +1. */
    val defaultServing: com.cansin.app.data.Serving = com.cansin.app.data.Serving.DRAFT,
    /** P31: show the ‰ estimate on widgets (needs a body profile). Off by default. */
    val widgetPromille: Boolean = false,
    val shareName: String? = null,
    val currency: Currency = Currency.NONE,
    val careEnabled: Boolean = true,
    val careLateHour: Int = CareRules.DEFAULT_LATE_HOUR,
    val homePlace: String? = null,
    val beerLimit: Int? = null,
    val budgetMinor: Long? = null,
    val pinSet: Boolean = false,
    val pinHidesWidget: Boolean = true,
) {
    companion object {
        const val DEFAULT_REMINDER_INTERVAL_MIN = 10
        const val MIN_REMINDER_INTERVAL_MIN = 5
        const val MAX_REMINDER_INTERVAL_MIN = 60
    }
}

/** Hide counts on widget + tile when a PIN is set (user option, default on). */
val Settings.hideCounts: Boolean get() = pinSet && pinHidesWidget

data class PinAttempts(val fails: Int = 0, val lastFailAt: Long? = null)

data class ReminderState(val nextAt: Long? = null, val sessionId: Long? = null, val stoppedSessionId: Long? = null)

inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

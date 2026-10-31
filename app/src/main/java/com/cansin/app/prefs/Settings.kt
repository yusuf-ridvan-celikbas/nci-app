package com.cansin.app.prefs

import androidx.annotation.StringRes
import com.cansin.app.R
import com.cansin.app.domain.BeerSize
import com.cansin.app.domain.BodyProfile

enum class AppName(@StringRes val label: Int, val alias: String) {
    SOY_TU_TENTACION(R.string.name_soy, "com.cansin.app.LauncherSoy"),
    SALGAM_BALGAM(R.string.name_salgam, "com.cansin.app.LauncherSalgam"),
}

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

data class Settings(
    val ageConfirmed: Boolean = false,
    val appName: AppName = AppName.SOY_TU_TENTACION,
    val language: Language = Language.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.AMBER,
    val remindersEnabled: Boolean = false,
    val reminderIntervalMin: Int = DEFAULT_REMINDER_INTERVAL_MIN,
    val reminderSilent: Boolean = false,
    val beerSize: BeerSize = BeerSize.DEFAULT,
    val profile: BodyProfile? = null,
    val profilePromptDismissed: Boolean = false,
) {
    companion object {
        const val DEFAULT_REMINDER_INTERVAL_MIN = 10
        const val MIN_REMINDER_INTERVAL_MIN = 5
        const val MAX_REMINDER_INTERVAL_MIN = 60
    }
}

data class ReminderState(val nextAt: Long? = null, val sessionId: Long? = null, val stoppedSessionId: Long? = null)

inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

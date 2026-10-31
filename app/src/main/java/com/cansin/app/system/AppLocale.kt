package com.cansin.app.system

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import com.cansin.app.prefs.Language
import java.util.Locale

object AppLocale {
    fun current(context: Context, stored: Language): Language =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Language.fromTags(localeManager(context).applicationLocales.toLanguageTags())
        } else {
            stored
        }

    fun apply(activity: Activity, language: Language) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            localeManager(activity).applicationLocales =
                language.tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        } else {
            activity.recreate()
        }
    }

    fun wrap(base: Context, language: Language): Context {
        val tag = language.tag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || tag == null) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun localeManager(context: Context) = context.getSystemService(LocaleManager::class.java)
}

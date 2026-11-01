package com.cansin.app.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {
    @Test
    fun unknownStoredValueFallsBackToDefault() {
        assertEquals(Accent.AMBER, enumOr("PINK", Accent.AMBER))
        assertEquals(ThemeMode.SYSTEM, enumOr(null, ThemeMode.SYSTEM))
    }

    @Test
    fun languageFromPlatformTags() {
        assertEquals(Language.SYSTEM, Language.fromTags(""))
        assertEquals(Language.TURKISH, Language.fromTags("tr-TR"))
        assertEquals(Language.ENGLISH, Language.fromTags("en-US,tr"))
        assertEquals(Language.SYSTEM, Language.fromTags("de"))
    }
}

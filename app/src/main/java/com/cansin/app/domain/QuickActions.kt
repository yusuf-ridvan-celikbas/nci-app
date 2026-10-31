package com.cansin.app.domain

/** Launcher shortcut targets (res/xml/shortcuts.xml extra values). Unknown values are ignored. */
enum class QuickAction(val key: String) {
    BEER("beer"),
    WATER("water"),
    TAB("tab");

    companion object {
        fun parse(value: String?): QuickAction? = entries.firstOrNull { it.key == value }

        /** The tile adds directly only when it can't surprise: age confirmed and a solo session (versus needs a side). */
        fun tileAddsDirectly(ageConfirmed: Boolean, versus: Boolean): Boolean = ageConfirmed && !versus
    }
}

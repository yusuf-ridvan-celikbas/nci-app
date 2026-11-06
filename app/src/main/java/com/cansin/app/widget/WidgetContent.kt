package com.cansin.app.widget

import com.cansin.app.data.SessionMode
import com.cansin.app.domain.WidgetPage

/** P31 per-widget setting: Auto (by size) or one thing only. */
enum class WidgetFocus { AUTO, DRINKS, WATER, TAB, PROMILLE }

/** Rows of the strip / big widget. */
enum class WidgetBlock { DRINKS, VERSUS, WATER, TIMER, TAB, PROMILLE, HERO, TAB_LIST }

/** One row of the XL Tab list: a drink line or a note (by index), plus how many didn't fit. */
data class TabRows(val lines: List<Int>, val notes: List<Int>, val overflow: Int)

/** What a widget shows for its size, the night's mode, its setting and the ‰ switch. Pure, unit-tested. */
object WidgetContent {
    /** ‰ is offered only with the widget switch on and a body profile set. */
    fun promilleOn(settingOn: Boolean, hasProfile: Boolean) = settingOn && hasProfile

    /** A focus that can't be shown (‰ off) falls back to Auto. */
    fun focus(stored: WidgetFocus, promille: Boolean) = if (stored == WidgetFocus.PROMILLE && !promille) WidgetFocus.AUTO else stored

    /** SMALL widget pages (‹ › arrows). */
    fun pages(focus: WidgetFocus, promille: Boolean): List<WidgetPage> = when (focus) {
        WidgetFocus.AUTO -> listOf(WidgetPage.BEER, WidgetPage.TAB, WidgetPage.WATER) + if (promille) listOf(WidgetPage.PROMILLE) else emptyList()
        WidgetFocus.DRINKS -> listOf(WidgetPage.BEER)
        WidgetFocus.WATER -> listOf(WidgetPage.WATER)
        WidgetFocus.TAB -> listOf(WidgetPage.TAB)
        WidgetFocus.PROMILLE -> listOf(WidgetPage.PROMILLE)
    }

    /** Stored page index → a page of this widget (wraps). */
    fun page(pages: List<WidgetPage>, index: Int): WidgetPage = pages[Math.floorMod(index, pages.size)]

    /** Rows for STRIP / BIG. Hidden (PIN) → nothing; the renderer shows "•••". */
    fun blocks(size: WidgetSize, focus: WidgetFocus, mode: SessionMode?, promille: Boolean, hidden: Boolean): List<WidgetBlock> {
        if (hidden) return emptyList()
        val drinks = if (mode == SessionMode.VERSUS) WidgetBlock.VERSUS else WidgetBlock.DRINKS
        if (size == WidgetSize.XL) {
            val hero = if (mode == SessionMode.VERSUS) WidgetBlock.VERSUS else WidgetBlock.HERO
            return when (focus) {
                WidgetFocus.AUTO -> listOfNotNull(hero, WidgetBlock.WATER, WidgetBlock.TIMER, WidgetBlock.PROMILLE.takeIf { promille }, WidgetBlock.TAB_LIST)
                WidgetFocus.DRINKS -> listOf(hero, WidgetBlock.TIMER)
                WidgetFocus.WATER -> listOf(WidgetBlock.WATER)
                WidgetFocus.TAB -> listOf(WidgetBlock.TAB_LIST)
                WidgetFocus.PROMILLE -> listOf(WidgetBlock.PROMILLE)
            }
        }
        return when (focus) {
            WidgetFocus.DRINKS -> if (size == WidgetSize.BIG) listOf(drinks, WidgetBlock.TIMER) else listOf(drinks)
            WidgetFocus.WATER -> listOf(WidgetBlock.WATER)
            WidgetFocus.TAB -> listOf(WidgetBlock.TAB)
            WidgetFocus.PROMILLE -> listOf(WidgetBlock.PROMILLE)
            WidgetFocus.AUTO -> when (size) {
                WidgetSize.STRIP, WidgetSize.COLUMN -> if (mode == SessionMode.VERSUS) listOf(drinks, WidgetBlock.TAB) else listOf(drinks, WidgetBlock.WATER, WidgetBlock.TAB)
                WidgetSize.BIG -> listOfNotNull(drinks, WidgetBlock.WATER, WidgetBlock.TIMER, WidgetBlock.TAB, WidgetBlock.PROMILLE.takeIf { promille })
                else -> listOf(drinks)
            }
        }
    }

    const val XL_TAB_ROWS = 8

    /** XL Tab list: drink lines first, then the newest notes, at most [max]; the rest is "+N more". */
    fun tabRows(lineCount: Int, noteCount: Int, max: Int = XL_TAB_ROWS): TabRows {
        val lines = (0 until minOf(lineCount, max)).toList()
        val notes = (0 until minOf(noteCount, max - lines.size)).toList()
        return TabRows(lines, notes, (lineCount + noteCount - lines.size - notes.size).coerceAtLeast(0))
    }

    /** TINY shows one number: the focus, else drinks. */
    fun tiny(focus: WidgetFocus): WidgetPage = when (focus) {
        WidgetFocus.WATER -> WidgetPage.WATER
        WidgetFocus.TAB -> WidgetPage.TAB
        WidgetFocus.PROMILLE -> WidgetPage.PROMILLE
        else -> WidgetPage.BEER
    }
}

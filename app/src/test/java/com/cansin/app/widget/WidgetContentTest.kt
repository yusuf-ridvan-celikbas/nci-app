package com.cansin.app.widget

import com.cansin.app.data.SessionMode
import com.cansin.app.domain.WidgetPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetContentTest {
    @Test
    fun promilleNeedsSwitchAndProfile() {
        assertTrue(WidgetContent.promilleOn(settingOn = true, hasProfile = true))
        assertFalse(WidgetContent.promilleOn(settingOn = true, hasProfile = false))
        assertFalse(WidgetContent.promilleOn(settingOn = false, hasProfile = true))
        assertEquals(WidgetFocus.AUTO, WidgetContent.focus(WidgetFocus.PROMILLE, promille = false))
        assertEquals(WidgetFocus.PROMILLE, WidgetContent.focus(WidgetFocus.PROMILLE, promille = true))
    }

    @Test
    fun smallPagesFollowFocusAndPromille() {
        assertEquals(listOf(WidgetPage.BEER, WidgetPage.TAB, WidgetPage.WATER), WidgetContent.pages(WidgetFocus.AUTO, false))
        assertEquals(4, WidgetContent.pages(WidgetFocus.AUTO, true).size)
        assertEquals(listOf(WidgetPage.WATER), WidgetContent.pages(WidgetFocus.WATER, true))
        val pages = WidgetContent.pages(WidgetFocus.AUTO, false)
        assertEquals(WidgetPage.TAB, WidgetContent.page(pages, 4))
        assertEquals(WidgetPage.WATER, WidgetContent.page(pages, -1))
    }

    @Test
    fun blocksPerSizeAndMode() {
        assertEquals(
            listOf(WidgetBlock.DRINKS, WidgetBlock.WATER, WidgetBlock.TIMER, WidgetBlock.TAB),
            WidgetContent.blocks(WidgetSize.BIG, WidgetFocus.AUTO, SessionMode.SOLO, promille = false, hidden = false),
        )
        assertEquals(
            listOf(WidgetBlock.VERSUS, WidgetBlock.WATER, WidgetBlock.TIMER, WidgetBlock.TAB, WidgetBlock.PROMILLE),
            WidgetContent.blocks(WidgetSize.BIG, WidgetFocus.AUTO, SessionMode.VERSUS, promille = true, hidden = false),
        )
        assertEquals(
            listOf(WidgetBlock.DRINKS, WidgetBlock.WATER, WidgetBlock.TAB),
            WidgetContent.blocks(WidgetSize.STRIP, WidgetFocus.AUTO, null, promille = true, hidden = false),
        )
        assertEquals(listOf(WidgetBlock.VERSUS, WidgetBlock.TAB), WidgetContent.blocks(WidgetSize.STRIP, WidgetFocus.AUTO, SessionMode.VERSUS, false, false))
        assertEquals(listOf(WidgetBlock.WATER), WidgetContent.blocks(WidgetSize.BIG, WidgetFocus.WATER, SessionMode.SOLO, true, false))
        assertEquals(listOf(WidgetBlock.DRINKS, WidgetBlock.TIMER), WidgetContent.blocks(WidgetSize.BIG, WidgetFocus.DRINKS, SessionMode.GROUP, true, false))
    }

    @Test
    fun pinHidesEverything() {
        WidgetSize.entries.forEach { size ->
            assertEquals(emptyList<WidgetBlock>(), WidgetContent.blocks(size, WidgetFocus.AUTO, SessionMode.SOLO, true, hidden = true))
        }
    }

    @Test
    fun tinyShowsTheFocus() {
        assertEquals(WidgetPage.BEER, WidgetContent.tiny(WidgetFocus.AUTO))
        assertEquals(WidgetPage.TAB, WidgetContent.tiny(WidgetFocus.TAB))
    }
}

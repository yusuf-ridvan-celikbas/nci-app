package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class TimerRulesTest {
    private val beer = BeerEvent(id = 3, sessionId = 1, at = 0)
    private val running = TimerState(3, 1, 100)

    @Test
    fun startNeedsEnabledIdleAndUnfinishedBeer() {
        assertEquals(true, TimerRules.canStart(true, TimerState(), beer))
        assertEquals(false, TimerRules.canStart(false, TimerState(), beer))
        assertEquals(false, TimerRules.canStart(true, running, beer))
        assertEquals(false, TimerRules.canStart(true, TimerState(), null))
        assertEquals(false, TimerRules.canStart(true, TimerState(), beer.copy(finishedAt = 5)))
    }

    @Test
    fun currentOnlyForSameSession() {
        assertEquals(true, TimerRules.isCurrent(running, 1))
        assertEquals(false, TimerRules.isCurrent(running, 2))
        assertEquals(false, TimerRules.isCurrent(TimerState(), 1))
    }

    @Test
    fun pagesWrapAround() {
        assertEquals(WidgetPage.WATER, TimerRules.step(WidgetPage.BEER, -1))
        assertEquals(WidgetPage.BEER, TimerRules.step(WidgetPage.WATER, 1))
        assertEquals(WidgetPage.TAB, TimerRules.page(4))
        assertEquals(WidgetPage.BEER, TimerRules.page(-3))
    }
}

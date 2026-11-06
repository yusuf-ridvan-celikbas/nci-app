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
    fun autoStartOnNewDrink() {
        val beer = BeerEvent(id = 7, sessionId = 1, at = 100)
        // Idle → starts for the new drink.
        assertEquals(TimerRules.AutoStart(null, TimerState(7, 1, 500)), TimerRules.autoStart(true, true, TimerState(), beer, 500))
        // Running for the previous drink → that one is finished, the new one starts.
        val running = TimerState(3, 1, 50)
        assertEquals(TimerRules.AutoStart(3, TimerState(7, 1, 500)), TimerRules.autoStart(true, true, running, beer, 500))
        // Old session's timer is not finished in this session.
        assertEquals(null, TimerRules.autoStart(true, true, TimerState(3, 9, 50), beer, 500)?.finishPrevious)
        // Setting off / timer off / already this drink → nothing.
        assertEquals(null, TimerRules.autoStart(true, false, TimerState(), beer, 500))
        assertEquals(null, TimerRules.autoStart(false, true, TimerState(), beer, 500))
        assertEquals(null, TimerRules.autoStart(true, true, TimerState(7, 1, 50), beer, 500))
        assertEquals(true, TimerRules.clearsFor(TimerState(7, 1, 50), 7))
        assertEquals(false, TimerRules.clearsFor(TimerState(7, 1, 50), 8))
    }
}

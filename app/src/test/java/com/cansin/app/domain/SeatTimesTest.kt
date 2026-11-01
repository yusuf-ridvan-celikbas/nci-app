package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.GroupSeat
import com.cansin.app.data.Side
import com.cansin.app.data.WaterEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatTimesTest {
    private val min = 60_000L
    private val seats = listOf(GroupSeat(1, Side.BLUE), GroupSeat(1, Side.RED))
    private val beers = listOf(
        BeerEvent(id = 1, sessionId = 1, side = Side.RED, at = 10 * min, finishedAt = 20 * min),
        BeerEvent(id = 2, sessionId = 1, side = Side.RED, at = 30 * min),
        BeerEvent(id = 3, sessionId = 1, side = Side.BLUE, at = 40 * min, finishedAt = 45 * min),
    )
    private val water = listOf(WaterEvent(sessionId = 1, at = 5 * min, side = Side.RED), WaterEvent(sessionId = 1, at = 50 * min, side = Side.RED))

    @Test
    fun timesPerSeatInSeatOrder() {
        val t = SeatTimeRules.times(seats, beers, water)
        assertEquals(listOf(Side.RED, Side.BLUE), t.map { it.side })
        val red = t[0]
        assertEquals(2, red.drinks)
        assertEquals(2, red.water)
        assertEquals(30 * min, red.lastDrinkAt)
        assertEquals(50 * min, red.lastWaterAt)
        assertEquals(5 * min, red.firstAt)
        assertEquals(2L, red.openDrink?.id)
        val blue = t[1]
        assertNull(blue.openDrink)
        assertNull(blue.lastWaterAt)
        assertEquals(40 * min, blue.firstAt)
    }

    @Test
    fun emptySeatHasNoTimes() {
        val t = SeatTimeRules.times(listOf(GroupSeat(1, Side.GREEN)), beers, water).single()
        assertEquals(0, t.drinks)
        assertNull(t.lastDrinkAt)
        assertNull(t.firstAt)
    }

    @Test
    fun minutesNeverNegative() {
        assertEquals(12, SeatTimeRules.minutesSince(0, 12 * min + 59_000))
        assertEquals(0, SeatTimeRules.minutesSince(10 * min, 0))
    }

    @Test
    fun timerStartsOnlyOnOpenDrink() {
        val (red, blue) = SeatTimeRules.times(seats, beers, water)
        assertTrue(SeatTimeRules.canStart(true, null, red))
        assertFalse(SeatTimeRules.canStart(false, null, red))
        assertFalse(SeatTimeRules.canStart(true, null, blue))
        assertFalse(SeatTimeRules.canStart(true, SeatTimer(Side.RED, 2, 0), red))
        assertTrue(SeatTimeRules.isValid(SeatTimer(Side.RED, 2, 0), red))
        assertFalse(SeatTimeRules.isValid(SeatTimer(Side.RED, 1, 0), red))
    }

    @Test
    fun codecRoundTripsAndIsStrict() {
        val timers = listOf(SeatTimer(Side.GREEN, 9, 100), SeatTimer(Side.RED, 2, 50))
        val raw = SeatTimeRules.encode(7, timers)
        assertEquals("7|RED:2:50,GREEN:9:100", raw)
        assertEquals(timers.associateBy { it.side }, SeatTimeRules.decode(raw, 7))
        assertTrue(SeatTimeRules.decode(raw, 8).isEmpty())
        assertTrue(SeatTimeRules.decode(null, 7).isEmpty())
        assertTrue(SeatTimeRules.decode("7|", 7).isEmpty())
        assertEquals(setOf(Side.RED), SeatTimeRules.decode("7|RED:2:50,SOLO:1:1,BLUE:x:1,GREEN:-1:5,RED:3:3", 7).keys)
        assertTrue(SeatTimeRules.decode("7|" + "RED:1:1,".repeat(200), 7).isEmpty())
    }
}

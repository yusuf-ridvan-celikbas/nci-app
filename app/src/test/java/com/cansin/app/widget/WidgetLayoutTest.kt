package com.cansin.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {
    @Test
    fun wideLayoutFromThreshold() {
        assertFalse(WidgetLayout.isWide(WidgetLayout.WIDE_MIN_WIDTH_DP - 1))
        assertTrue(WidgetLayout.isWide(WidgetLayout.WIDE_MIN_WIDTH_DP))
    }

    @Test
    fun chronometerBaseOffsetsElapsedByAge() {
        assertEquals(40_000L, WidgetLayout.chronometerBase(nowWall = 1_060_000, nowElapsed = 100_000, lastBeerAt = 1_000_000))
    }

    @Test
    fun futureLastBeerIsClampedToNow() {
        assertEquals(100_000L, WidgetLayout.chronometerBase(nowWall = 1_000_000, nowElapsed = 100_000, lastBeerAt = 2_000_000))
    }
}

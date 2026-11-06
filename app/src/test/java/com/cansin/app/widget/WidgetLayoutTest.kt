package com.cansin.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutTest {
    @Test
    fun sizeClassesByLauncherSize() {
        assertEquals(WidgetSize.TINY, WidgetLayout.sizeClass(57, 57)) // 1×1
        assertEquals(WidgetSize.TINY, WidgetLayout.sizeClass(140, 60)) // 2×1
        assertEquals(WidgetSize.STRIP, WidgetLayout.sizeClass(200, 70)) // 3×1
        assertEquals(WidgetSize.STRIP, WidgetLayout.sizeClass(320, 60)) // 4×1
        assertEquals(WidgetSize.TALL, WidgetLayout.sizeClass(70, 150)) // 1×2: narrow pages
        assertEquals(WidgetSize.TALL, WidgetLayout.sizeClass(140, 140)) // 2×2: narrow pages
        assertEquals(WidgetSize.TALL, WidgetLayout.sizeClass(140, 330)) // 2×4: narrow pages
        assertEquals(WidgetSize.TALL, WidgetLayout.sizeClass(70, 320)) // 1×4: narrow pages
        assertEquals(WidgetSize.SMALL, WidgetLayout.sizeClass(200, 150)) // 3×2
        assertEquals(WidgetSize.SMALL, WidgetLayout.sizeClass(320, 150)) // 4×2
        assertEquals(WidgetSize.BIG, WidgetLayout.sizeClass(320, 250)) // 4×3
        assertEquals(WidgetSize.XL, WidgetLayout.sizeClass(320, 340)) // 4×4
        assertEquals(WidgetSize.XL, WidgetLayout.sizeClass(400, 430)) // 5×5
    }

    @Test
    fun chronometerBaseOffsetsElapsedByAge() {
        assertEquals(40_000L, WidgetLayout.chronometerBase(nowWall = 1_060_000, nowElapsed = 100_000, lastBeerAt = 1_000_000))
    }

    @Test
    fun futureLastBeerIsClampedToNow() {
        assertEquals(100_000L, WidgetLayout.chronometerBase(nowWall = 1_000_000, nowElapsed = 100_000, lastBeerAt = 2_000_000))
    }

    @Test
    fun narrowButtonsGrowWithHeight() {
        assertEquals(0, WidgetLayout.narrowScale(150)) // 2×2
        assertEquals(1, WidgetLayout.narrowScale(240)) // 2×3
        assertEquals(2, WidgetLayout.narrowScale(400)) // 2×5
    }
}

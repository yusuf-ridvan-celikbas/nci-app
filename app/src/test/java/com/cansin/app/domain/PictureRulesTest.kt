package com.cansin.app.domain

import com.cansin.app.data.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureRulesTest {
    private val eps = 0.01f

    @Test
    fun keys() {
        assertEquals("friend_7", PictureRules.keyFor(3, Side.RED, 7))
        assertEquals("s3_BLUE", PictureRules.keyFor(3, Side.BLUE, null))
        assertNull(PictureRules.keyFor(null, Side.BLUE, null))
        assertEquals("draft_RED", PictureRules.draft(Side.RED))
        listOf("me", "friend_1", "s12_RED", "draft_BLUE").forEach { assertTrue(it, PictureRules.isValid(it)) }
        listOf("", "../me", "friend_", "s_RED", "me.jpg", "x").forEach { assertFalse(it, PictureRules.isValid(it)) }
    }

    @Test
    fun keepOnlyOpenNightsAndOptionalDrafts() {
        assertTrue(PictureRules.keep("me", null, false))
        assertTrue(PictureRules.keep("friend_4", null, false))
        assertTrue(PictureRules.keep("s5_RED", 5, false))
        assertFalse(PictureRules.keep("s5_RED", 6, false))
        assertFalse(PictureRules.keep("s5_RED", null, false))
        assertFalse(PictureRules.keep("s51_RED", 5, false))
        assertTrue(PictureRules.keep("draft_RED", null, true))
        assertFalse(PictureRules.keep("draft_RED", null, false))
        assertFalse(PictureRules.keep("junk", null, true))
    }

    @Test
    fun sampleSizeKeepsLongSideUnderLimit() {
        assertEquals(1, PictureRules.sampleSize(1000, 800, 2048))
        assertEquals(2, PictureRules.sampleSize(4096, 3072, 2048))
        assertEquals(4, PictureRules.sampleSize(8192, 100, 2048))
    }

    @Test
    fun noZoomCentredTakesMiddleSquare() {
        val crop = PictureRules.crop(400, 200, 100f, 1f, 0f, 0f)
        assertEquals(200f, crop.size, eps)
        assertEquals(100f, crop.left, eps)
        assertEquals(0f, crop.top, eps)
    }

    @Test
    fun zoomShrinksSquareAndOffsetMovesIt() {
        // 400×200 drawn so 200 px covers 100 view px (k = 0.5); zoom 2 → k = 1.
        val crop = PictureRules.crop(400, 200, 100f, 2f, 0f, 0f)
        assertEquals(100f, crop.size, eps)
        assertEquals(150f, crop.left, eps)
        assertEquals(50f, crop.top, eps)
        // Dragging the image right shows more of its left side.
        val moved = PictureRules.crop(400, 200, 100f, 2f, 40f, 0f)
        assertEquals(110f, moved.left, eps)
    }

    @Test
    fun offsetIsClampedSoCircleStaysCovered() {
        val (mx, my) = PictureRules.maxOffset(400, 200, 100f, 1f)
        assertEquals(50f, mx, eps)
        assertEquals(0f, my, eps)
        val crop = PictureRules.crop(400, 200, 100f, 1f, 999f, -999f)
        assertEquals(0f, crop.left, eps)
        assertEquals(0f, crop.top, eps)
        assertEquals(200f, crop.size, eps)
    }

    @Test
    fun zoomIsLimited() {
        assertEquals(1f, PictureRules.clampZoom(0.2f), eps)
        assertEquals(PictureRules.MAX_ZOOM, PictureRules.clampZoom(50f), eps)
    }
}

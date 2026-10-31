package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.Note
import com.cansin.app.data.Session
import com.cansin.app.data.SessionMode
import com.cansin.app.data.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VersusRulesTest {
    private fun note(side: Side?, amount: Long?) = Note(sessionId = 1, text = "n", amountMinor = amount, createdAt = 0, side = side)

    @Test
    fun totalsPerSide() {
        val totals = VersusRules.totals(listOf(note(Side.RED, 1_000), note(Side.BLUE, 250), note(null, 500), note(Side.RED, null)))
        assertEquals(TabTotals(red = 1_000, blue = 250, shared = 500), totals)
        assertEquals(1_750, totals.total)
    }

    @Test
    fun sideFitsMode() {
        assertEquals(true, VersusRules.fits(Side.SOLO, null))
        assertEquals(true, VersusRules.fits(Side.SOLO, SessionMode.SOLO))
        assertEquals(false, VersusRules.fits(Side.SOLO, SessionMode.VERSUS))
        assertEquals(true, VersusRules.fits(Side.RED, SessionMode.VERSUS))
        assertEquals(false, VersusRules.fits(Side.BLUE, null))
    }

    @Test
    fun mineDependsOnSession() {
        val solo = Session(startedAt = 0)
        val versus = Session(startedAt = 0, mode = SessionMode.VERSUS, meSide = Side.RED)
        assertEquals(true, VersusRules.isMine(BeerEvent(sessionId = 1, at = 0), solo))
        assertEquals(true, VersusRules.isMine(BeerEvent(sessionId = 1, side = Side.RED, at = 0), versus))
        assertEquals(false, VersusRules.isMine(BeerEvent(sessionId = 1, side = Side.BLUE, at = 0), versus))
        assertNull(VersusRules.meSide(versus.copy(meSide = null)))
    }

    @Test
    fun cleansNames() {
        assertNull(VersusRules.cleanName("   "))
        assertEquals("Ali", VersusRules.cleanName(" Ali "))
    }
}

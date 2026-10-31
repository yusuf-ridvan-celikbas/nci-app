package com.cansin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeerRulesTest {
    @Test
    fun parsesCentilitres() {
        assertEquals(330, BeerRules.parseCl("33"))
        assertEquals(568, BeerRules.parseCl("56,8"))
        assertEquals(400, BeerRules.parseCl(" 40.0 "))
        assertEquals(BeerRules.MIN_ML, BeerRules.parseCl("10"))
        assertEquals(BeerRules.MAX_ML, BeerRules.parseCl("200"))
    }

    @Test
    fun rejectsInvalidCentilitres() {
        listOf("", "9.9", "200.1", "-33", "33.25", "abc", "1e2", ",5").forEach { assertNull(it, BeerRules.parseCl(it)) }
    }

    @Test
    fun parsesAbv() {
        assertEquals(5.0, BeerRules.parseAbv("5")!!, 0.0)
        assertEquals(4.7, BeerRules.parseAbv("4,7")!!, 1e-9)
        assertEquals(0.0, BeerRules.parseAbv("0")!!, 0.0)
        listOf("20.1", "-1", "5.55", "").forEach { assertNull(it, BeerRules.parseAbv(it)) }
    }

    @Test
    fun formats() {
        assertEquals("50", BeerRules.formatCl(500, ','))
        assertEquals("56,8", BeerRules.formatCl(568, ','))
        assertEquals("5.0", BeerRules.formatAbv(5.0, '.'))
        assertEquals("4,7", BeerRules.formatAbv(4.7, ','))
    }

    @Test
    fun clampsOutOfRange() {
        assertEquals(BeerSize(BeerRules.MAX_ML, BeerRules.MIN_ABV), BeerRules.clamp(BeerSize(99_999, -3.0)))
    }
}

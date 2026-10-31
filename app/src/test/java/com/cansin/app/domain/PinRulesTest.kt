package com.cansin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinRulesTest {
    @Test
    fun validatesPins() {
        listOf("1234", "000000", "98765").forEach { assertTrue(it, PinRules.isValid(it)) }
        listOf("", "123", "1234567", "12a4", "１２３４", "12 34").forEach { assertFalse(it, PinRules.isValid(it)) }
    }

    @Test
    fun hashesAndVerifies() {
        val stored = PinRules.hash("2468", iterations = 1_000)
        assertFalse(stored.contains("2468"))
        assertTrue(PinRules.verify("2468", stored))
        assertFalse(PinRules.verify("2469", stored))
        assertFalse(PinRules.verify("2468", null))
        assertNotEquals(stored, PinRules.hash("2468", iterations = 1_000))
    }

    @Test
    fun rejectsTamperedHashes() {
        val stored = PinRules.hash("2468", iterations = 1_000)
        listOf("", "x", stored.replace("pbkdf2-sha256", "md5"), stored.replace("$1000$", "$0$"), "pbkdf2-sha256\$1000\$!!\$!!", "$stored\$extra")
            .forEach { assertFalse(it, PinRules.verify("2468", it)) }
    }

    @Test
    fun backsOffAfterFreeTries() {
        assertEquals(0, PinRules.waitAfter(4))
        assertEquals(30_000, PinRules.waitAfter(5))
        assertEquals(60_000, PinRules.waitAfter(6))
        assertEquals(PinRules.MAX_WAIT_MS, PinRules.waitAfter(50))
        assertEquals(0, PinRules.remainingWait(4, 1_000, 1_000))
        assertEquals(20_000, PinRules.remainingWait(5, 1_000, 11_000))
        assertEquals(0, PinRules.remainingWait(5, 1_000, 40_000))
        assertEquals(30_000, PinRules.remainingWait(5, 50_000, 1_000))
        assertEquals(0, PinRules.remainingWait(5, null, 1_000))
    }

    @Test
    fun locksOnColdStartOrAfterAMinute() {
        assertFalse(PinRules.shouldLock(false, null, 0))
        assertTrue(PinRules.shouldLock(true, null, 0))
        assertFalse(PinRules.shouldLock(true, 1_000, 30_000))
        assertTrue(PinRules.shouldLock(true, 1_000, 61_000))
        assertTrue(PinRules.shouldLock(true, 10_000, 5_000))
    }
}

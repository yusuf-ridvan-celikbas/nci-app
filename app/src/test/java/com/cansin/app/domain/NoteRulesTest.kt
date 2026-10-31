package com.cansin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class NoteRulesTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private fun at(day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun parsesAmounts() {
        assertEquals(AmountInput.Empty, NoteRules.parseAmount("  "))
        assertEquals(AmountInput.Valid(1250), NoteRules.parseAmount("12.5"))
        assertEquals(AmountInput.Valid(1250), NoteRules.parseAmount("12,50"))
        assertEquals(AmountInput.Valid(700), NoteRules.parseAmount("7"))
        assertEquals(AmountInput.Valid(5), NoteRules.parseAmount("0.05"))
    }

    @Test
    fun rejectsInvalidAmounts() {
        listOf("-1", "1.234", "abc", "1e5", "1.2.3", ".5", "9999999999").forEach {
            assertEquals(it, AmountInput.Invalid, NoteRules.parseAmount(it))
        }
        assertEquals(AmountInput.Invalid, NoteRules.parseAmount("1000000.01"))
        assertEquals(AmountInput.Valid(NoteRules.MAX_AMOUNT_MINOR), NoteRules.parseAmount("1000000"))
    }

    @Test
    fun formatsAmounts() {
        assertEquals("12,05", NoteRules.formatAmount(1205, ','))
        assertEquals("0.00", NoteRules.formatAmount(0, '.'))
    }

    @Test
    fun cleansText() {
        assertNull(NoteRules.cleanText("  "))
        assertEquals("hi", NoteRules.cleanText("  hi "))
        assertNull(NoteRules.cleanPerson(" "))
    }

    @Test
    fun pickTimeKeepsSameDay() {
        assertEquals(at(30, 21, 15), NoteRules.pickTime(at(30, 22, 0), 21, 15, zone))
    }

    @Test
    fun pickTimeCrossesMidnightBackwards() {
        assertEquals(at(30, 23, 50), NoteRules.pickTime(at(31, 0, 30), 23, 50, zone))
    }

    @Test
    fun pickTimeCrossesMidnightForwards() {
        assertEquals(at(31, 0, 10), NoteRules.pickTime(at(30, 23, 40), 0, 10, zone))
    }
}

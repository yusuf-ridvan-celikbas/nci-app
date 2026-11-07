package com.cansin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Base64

class ResultCardsTest {
    private val today = LocalDate.of(2026, 10, 31)
    private val card = ResultCard("Çağrı Ş", LocalDate.of(2026, 10, 30), 4, 190, 2)

    private fun raw(body: String) = ResultCards.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(body.toByteArray())

    @Test
    fun roundTripsInsideAChatMessage() {
        val code = ResultCards.encode(card)
        assertTrue(code.startsWith("Salgam-"))
        assertEquals(card, ResultCards.decode("Here's mine 🍺\n$code\nsee you!", today))
        assertEquals(card, ResultCards.decode("SALGAM-" + code.removePrefix(ResultCards.PREFIX), today))
        // Old codes still work, and an ordinary "Salgam-..." word before the code doesn't hide it.
        assertEquals(card, ResultCards.decode("cansin:1:" + code.removePrefix(ResultCards.PREFIX), today))
        assertEquals(card, ResultCards.decode("Salgam-balgam-night!! $code", today))
    }

    @Test
    fun buildsCardFromSession() {
        assertEquals(ResultCard("Ali", today, 4, 190, 2), ResultCards.card("  Ali ", today, 4, 190 * 60_000L + 59_000, 2))
        assertEquals(ResultCards.MAX_DURATION_MIN, ResultCards.card("Ali", today, 4, Long.MAX_VALUE / 2, 2)!!.durationMin)
        assertNull(ResultCards.card("  ", today, 4, 0, 0))
    }

    @Test
    fun rejectsGarbage() {
        listOf("", "hello", "Salgam-", "Salgam-!!!!!!!!!!", "cansin:1:", "cansin:2:" + ResultCards.encode(card).removePrefix(ResultCards.PREFIX), "x".repeat(6_000))
            .forEach { assertNull(it.take(40), ResultCards.decode(it, today)) }
    }

    @Test
    fun rejectsOutOfRangeOrMalformedFields() {
        listOf(
            "Ali\n2026-10-30\n101\n10\n0",
            "Ali\n2026-10-30\n4\n4321\n0",
            "Ali\n2026-10-30\n4\n10\n101",
            "Ali\n2026-10-30\n-1\n10\n0",
            "Ali\n2026-10-30\n4\n10",
            "Ali\n2026-10-30\n4\n10\n0\nextra",
            "Ali\n2026-13-01\n4\n10\n0",
            "Ali\n2026-11-02\n4\n10\n0",
            "Ali\n2019-12-31\n4\n10\n0",
            "\n2026-10-30\n4\n10\n0",
            " Ali\n2026-10-30\n4\n10\n0",
            "A\u202Eli\n2026-10-30\n4\n10\n0",
            "A\tli\n2026-10-30\n4\n10\n0",
            "${"x".repeat(31)}\n2026-10-30\n4\n10\n0",
        ).forEach { assertNull(it, ResultCards.decode(raw(it), today)) }
    }

    @Test
    fun rejectsInvalidUtf8() {
        val bytes = byteArrayOf(0xC3.toByte(), 0x28, '\n'.code.toByte())
        assertNull(ResultCards.decode(ResultCards.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes + "2026-10-30\n1\n1\n1".toByteArray()), today))
    }

    @Test
    fun acceptsTomorrowForTimeZones() {
        assertEquals(1, ResultCards.decode(raw("Ali\n2026-11-01\n1\n0\n0"), today)!!.beers)
    }
}

package com.cansin.app.domain

import com.cansin.app.data.Note
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

sealed interface AmountInput {
    data object Empty : AmountInput
    data object Invalid : AmountInput
    data class Valid(val minor: Long) : AmountInput
}

object NoteRules {
    const val MAX_AMOUNT_MINOR = 100_000_000L
    private val AMOUNT = Regex("""^(\d{1,9})(?:[.,](\d{1,2}))?$""")

    fun cleanText(raw: String): String? = raw.trim().take(Note.MAX_TEXT).ifEmpty { null }

    fun cleanPerson(raw: String?): String? = raw?.trim()?.take(Note.MAX_PERSON)?.ifEmpty { null }

    fun isValidAmount(minor: Long?): Boolean = minor == null || minor in 0..MAX_AMOUNT_MINOR

    fun parseAmount(raw: String): AmountInput {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return AmountInput.Empty
        val match = AMOUNT.matchEntire(trimmed) ?: return AmountInput.Invalid
        val whole = match.groupValues[1].toLong()
        val fraction = match.groupValues[2].padEnd(2, '0').ifEmpty { "00" }.toLong()
        val minor = whole * 100 + fraction
        return if (minor <= MAX_AMOUNT_MINOR) AmountInput.Valid(minor) else AmountInput.Invalid
    }

    fun formatAmount(minor: Long, decimalSeparator: Char): String =
        "${minor / 100}$decimalSeparator${(minor % 100).toString().padStart(2, '0')}"

    fun pickTime(createdAt: Long, hour: Int, minute: Int, zone: ZoneId): Long {
        val sameDay = Instant.ofEpochMilli(createdAt).atZone(zone)
            .withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        return listOf(sameDay.minusDays(1), sameDay, sameDay.plusDays(1))
            .map { it.toInstant().toEpochMilli() }
            .minBy { abs(it - createdAt) }
    }

    const val MAX_QUANTITY = 99

    /** Tab quick-add with a count: "Nuts" ×3 at 80 each → ("Nuts ×3", 240). Null if the total is too large. */
    fun withQuantity(text: String, eachMinor: Long?, quantity: Int): Pair<String, Long?>? {
        val q = quantity.coerceIn(1, MAX_QUANTITY)
        val total = eachMinor?.let { if (it > MAX_AMOUNT_MINOR / q) return null else it * q }
        val label = if (q > 1) "${text.trim()} ×$q" else text
        return label to total
    }
}

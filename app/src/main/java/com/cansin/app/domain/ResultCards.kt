package com.cansin.app.domain

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.util.Base64

/** A finished-session result shared between friends as text. Holds only name + counts (P13, no network). */
data class ResultCard(val name: String, val day: LocalDate, val beers: Int, val durationMin: Int, val water: Int)

object ResultCards {
    const val PREFIX = "Salgam-"
    const val MAX_BEERS = 100
    const val MAX_WATER = 100
    const val MAX_DURATION_MIN = 72 * 60
    private const val MAX_TEXT = 5_000
    private const val MAX_PAYLOAD = 400
    private val EARLIEST: LocalDate = LocalDate.of(2020, 1, 1)
    /** "Salgam-<payload>"; codes from before P40 ("cansin:1:<payload>") are still read. */
    private val CODE = Regex("""(?:Salgam-|cansin:1:)([A-Za-z0-9_-]{8,$MAX_PAYLOAD})""", RegexOption.IGNORE_CASE)

    /** Name is user-typed: trimmed, ≤30 chars, no control or bidi-override characters (anti-spoofing). */
    fun cleanName(raw: String?): String? = VersusRules.cleanName(raw)?.takeIf { name -> name.none(::isUnsafe) }

    fun card(name: String?, day: LocalDate, beers: Int, durationMs: Long, water: Int): ResultCard? {
        val clean = cleanName(name) ?: return null
        return ResultCard(
            name = clean,
            day = day,
            beers = beers.coerceIn(0, MAX_BEERS),
            durationMin = (durationMs / 60_000).coerceIn(0, MAX_DURATION_MIN.toLong()).toInt(),
            water = water.coerceIn(0, MAX_WATER),
        )
    }

    fun encode(card: ResultCard): String {
        val body = listOf(card.name, card.day.toString(), card.beers, card.durationMin, card.water).joinToString("\n")
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(body.toByteArray(Charsets.UTF_8))
    }

    /** Finds the first code anywhere in pasted text (e.g. a whole chat message). Null if missing or invalid. */
    fun decode(text: String, today: LocalDate): ResultCard? {
        if (text.length > MAX_TEXT) return null
        // "Salgam-" can also start ordinary words in a chat message, so try each match.
        return CODE.findAll(text).firstNotNullOfOrNull { decodePayload(it.groupValues[1], today) }
    }

    private fun decodePayload(payload: String, today: LocalDate): ResultCard? {
        val bytes = try {
            Base64.getUrlDecoder().decode(payload)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val body = strictUtf8(bytes) ?: return null
        val parts = body.split('\n')
        if (parts.size != 5) return null
        val name = cleanName(parts[0])?.takeIf { it == parts[0] } ?: return null
        val day = try {
            LocalDate.parse(parts[1])
        } catch (_: Exception) {
            return null
        }
        if (day.isBefore(EARLIEST) || day.isAfter(today.plusDays(1))) return null
        val beers = number(parts[2], MAX_BEERS) ?: return null
        val duration = number(parts[3], MAX_DURATION_MIN) ?: return null
        val water = number(parts[4], MAX_WATER) ?: return null
        return ResultCard(name, day, beers, duration, water)
    }

    private fun number(raw: String, max: Int): Int? =
        raw.takeIf { it.length in 1..4 && it.all { c -> c in '0'..'9' } }?.toInt()?.takeIf { it <= max }

    private fun strictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun isUnsafe(c: Char): Boolean {
        val code = c.code
        return Character.isISOControl(c) || code in 0x202A..0x202E || code in 0x2066..0x2069 ||
            code == 0x200E || code == 0x200F || code == 0x2028 || code == 0x2029 || code == 0xFEFF
    }
}

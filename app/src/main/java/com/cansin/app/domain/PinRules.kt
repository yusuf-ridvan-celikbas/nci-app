package com.cansin.app.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App lock PIN (P18). A family/privacy screen, not encryption: data stays app-private as before.
 * Stored as "pbkdf2-sha256$iterations$salt$hash" (Base64), never the PIN itself.
 */
object PinRules {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6
    const val FREE_TRIES = 5
    const val BASE_WAIT_MS = 30_000L
    const val MAX_WAIT_MS = 15 * 60_000L
    const val RELOCK_AFTER_MS = 60_000L
    const val ITERATIONS = 100_000
    private const val PREFIX = "pbkdf2-sha256"
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    fun isValid(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun hash(pin: String, iterations: Int = ITERATIONS, salt: ByteArray = randomSalt()): String {
        require(isValid(pin))
        val b64 = Base64.getEncoder()
        return listOf(PREFIX, iterations.toString(), b64.encodeToString(salt), b64.encodeToString(derive(pin, salt, iterations))).joinToString("$")
    }

    fun verify(pin: String, stored: String?): Boolean {
        if (stored == null || !isValid(pin)) return false
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it in 1..10_000_000 } ?: return false
        return try {
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            MessageDigest.isEqual(expected, derive(pin, salt, iterations))
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /** No wait for the first 5 wrong tries, then 30 s doubling per try, max 15 min. */
    fun waitAfter(fails: Int): Long {
        if (fails < FREE_TRIES) return 0
        val steps = (fails - FREE_TRIES).coerceAtMost(10)
        return (BASE_WAIT_MS shl steps).coerceAtMost(MAX_WAIT_MS)
    }

    /** Time left before the next try is allowed. A clock set backwards restarts the full wait. */
    fun remainingWait(fails: Int, lastFailAt: Long?, now: Long): Long {
        val wait = waitAfter(fails)
        if (wait == 0L || lastFailAt == null) return 0
        if (now < lastFailAt) return wait
        return (lastFailAt + wait - now).coerceAtLeast(0)
    }

    /** Lock on cold start, after 1 min in the background, or if the clock went backwards. */
    fun shouldLock(pinSet: Boolean, stoppedAt: Long?, now: Long): Boolean =
        pinSet && (stoppedAt == null || now < stoppedAt || now - stoppedAt >= RELOCK_AFTER_MS)

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun randomSalt() = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
}

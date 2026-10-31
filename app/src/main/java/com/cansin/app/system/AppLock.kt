package com.cansin.app.system

import com.cansin.app.domain.PinRules
import com.cansin.app.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface PinCheck {
    data object Ok : PinCheck
    data class Wrong(val waitMs: Long) : PinCheck
    data class Wait(val waitMs: Long) : PinCheck
}

/** PIN hashing + wrong-try counter (persisted, so killing the app doesn't reset the wait). */
class AppLock(private val store: SettingsStore) {
    private val mutex = Mutex()

    /** When the app last left the foreground in this process; null = cold start. */
    @Volatile var stoppedAt: Long? = null

    /** Unlocked in this process (survives rotation, not process death). */
    @Volatile var unlocked: Boolean = false

    suspend fun setPin(pin: String) {
        val hash = withContext(Dispatchers.Default) { PinRules.hash(pin) }
        store.setPinHash(hash)
        unlocked = true
    }

    suspend fun removePin() = store.setPinHash(null)

    suspend fun check(pin: String, now: Long = System.currentTimeMillis()): PinCheck = mutex.withLock {
        val attempts = store.pinAttempts()
        val wait = PinRules.remainingWait(attempts.fails, attempts.lastFailAt, now)
        if (wait > 0) return PinCheck.Wait(wait)
        val stored = store.pinHash()
        val ok = withContext(Dispatchers.Default) { PinRules.verify(pin, stored) }
        if (ok) {
            store.resetPinFails()
            unlocked = true
            PinCheck.Ok
        } else {
            store.recordPinFail(now)
            PinCheck.Wrong(PinRules.waitAfter(attempts.fails + 1))
        }
    }

    suspend fun remainingWait(now: Long = System.currentTimeMillis()): Long =
        store.pinAttempts().let { PinRules.remainingWait(it.fails, it.lastFailAt, now) }
}

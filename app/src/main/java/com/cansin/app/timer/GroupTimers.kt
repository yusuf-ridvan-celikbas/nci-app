package com.cansin.app.timer

import com.cansin.app.data.SessionRepository
import com.cansin.app.data.Side
import com.cansin.app.domain.SeatTimeRules
import com.cansin.app.domain.SeatTimer
import com.cansin.app.domain.SeatTimes
import com.cansin.app.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Drink timers for the other people in a Together session. Kept only while that session is open. */
class GroupTimers(
    private val store: SettingsStore,
    private val repository: () -> SessionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun running(sessionId: Long): Flow<Map<Side, SeatTimer>> = store.groupTimers.map { SeatTimeRules.decode(it, sessionId) }

    private suspend fun current(sessionId: Long) = SeatTimeRules.decode(store.groupTimers.first(), sessionId)

    suspend fun start(sessionId: Long, times: SeatTimes, enabled: Boolean) {
        val now = current(sessionId)
        val drink = times.openDrink
        if (!SeatTimeRules.canStart(enabled, now[times.side], times) || drink == null) return
        store.setGroupTimers(SeatTimeRules.encode(sessionId, now.values + SeatTimer(times.side, drink.id, clock())))
    }

    /** Stop saves the finish time on that drink. */
    suspend fun stop(sessionId: Long, side: Side) {
        val timer = drop(sessionId, side) ?: return
        repository().finishBeer(timer.beerId, sessionId)
    }

    /** Removes the timer without touching the drink; returns what was running. */
    suspend fun drop(sessionId: Long, side: Side): SeatTimer? {
        val now = current(sessionId)
        val timer = now[side] ?: return null
        store.setGroupTimers(SeatTimeRules.encode(sessionId, now.values - timer))
        return timer
    }

    suspend fun clear() = store.setGroupTimers(null)
}

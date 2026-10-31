package com.cansin.app.timer

import com.cansin.app.data.SessionRepository
import com.cansin.app.domain.TimerRules
import com.cansin.app.domain.TimerState
import com.cansin.app.prefs.SettingsStore
import kotlinx.coroutines.flow.first

class DrinkTimer(
    private val store: SettingsStore,
    private val repository: () -> SessionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun start(): Boolean {
        val snapshot = repository().snapshot()
        val beer = snapshot.myLatestBeer
        if (!TimerRules.canStart(store.settings.first().timerEnabled, store.timerState.first(), beer) || beer == null) return false
        store.setTimer(TimerState(beer.id, beer.sessionId, clock()))
        return true
    }

    suspend fun stop() {
        val state = store.timerState.first()
        store.setTimer(TimerState())
        val beerId = state.beerId ?: return
        val sessionId = state.sessionId ?: return
        repository().finishBeer(beerId, sessionId)
    }

    suspend fun clearIf(sessionId: Long) {
        if (store.timerState.first().sessionId == sessionId) store.setTimer(TimerState())
    }

    suspend fun clear() = store.setTimer(TimerState())
}

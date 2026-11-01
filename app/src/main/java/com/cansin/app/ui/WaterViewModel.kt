package com.cansin.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cansin.app.data.SessionRepository
import com.cansin.app.data.WaterEvent
import com.cansin.app.domain.VersusRules
import com.cansin.app.domain.WaterRules
import com.cansin.app.prefs.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WaterState(val available: Boolean = false, val count: Int = 0, val since: Int = 0, val nudge: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class WaterViewModel(private val repository: SessionRepository, settingsStore: SettingsStore) : ViewModel() {
    private val dismissedAt = MutableStateFlow<Int?>(null)

    private val counts = repository.openSession.flatMapLatest { session ->
        if (session == null || VersusRules.meSide(session) == null) {
            flowOf(WaterState())
        } else {
            combine(repository.beers(session.id), repository.water(session.id)) { beers, water ->
                val mine = beers.filter { VersusRules.isMine(it, session) }.map { it.at }
                val myWater = water.filter { VersusRules.isMineWater(it, session) }
                WaterState(true, myWater.size, WaterRules.beersSinceWater(mine, myWater.maxOfOrNull { it.at }))
            }
        }
    }

    val state: StateFlow<WaterState> = combine(counts, settingsStore.settings, dismissedAt) { s, settings, dismissed ->
        s.copy(nudge = s.available && WaterRules.shouldNudge(settings.waterEnabled, settings.waterEvery, s.since, dismissed))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaterState())

    private val added = Channel<WaterEvent>(Channel.CONFLATED)
    val waterAdded: Flow<WaterEvent> = added.receiveAsFlow()

    fun add() = viewModelScope.launch {
        repository.addWater()?.let {
            dismissedAt.value = null
            added.send(it)
        }
    }

    fun undo(event: WaterEvent) = viewModelScope.launch { repository.undoWater(event) }

    fun dismiss() {
        dismissedAt.value = state.value.since
    }

    companion object {
        fun factory(repository: SessionRepository, settingsStore: SettingsStore): ViewModelProvider.Factory =
            viewModelFactory { initializer { WaterViewModel(repository, settingsStore) } }
    }
}

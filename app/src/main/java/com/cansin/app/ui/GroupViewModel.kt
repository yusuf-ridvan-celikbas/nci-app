package com.cansin.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cansin.app.data.BeerEvent
import com.cansin.app.data.GroupSeat
import com.cansin.app.data.Session
import com.cansin.app.data.SessionMode
import com.cansin.app.data.SessionRepository
import com.cansin.app.data.Side
import com.cansin.app.data.WaterEvent
import com.cansin.app.domain.DrinkChoice
import com.cansin.app.domain.GroupRules
import com.cansin.app.domain.GroupSetup
import com.cansin.app.domain.SeatTimeRules
import com.cansin.app.domain.SeatTimer
import com.cansin.app.domain.SeatTimes
import com.cansin.app.domain.TimerState
import com.cansin.app.prefs.SettingsStore
import com.cansin.app.timer.DrinkTimer
import com.cansin.app.timer.GroupTimers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GroupState(
    val loaded: Boolean = false,
    val session: Session? = null,
    val seats: List<GroupSeat> = emptyList(),
    val times: List<SeatTimes> = emptyList(),
    /** Running timer start per seat; my seat comes from the normal drink timer. */
    val timers: Map<Side, Long> = emptyMap(),
    val timerEnabled: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class GroupViewModel(
    private val repository: SessionRepository,
    private val store: SettingsStore,
    private val drinkTimer: DrinkTimer,
    private val groupTimers: GroupTimers,
    private val onTimerChanged: suspend () -> Unit,
) : ViewModel() {
    private val enabled = store.settings.map { it.timerEnabled }

    val state: StateFlow<GroupState> = repository.openSession
        .flatMapLatest { session ->
            if (session?.mode != SessionMode.GROUP) {
                flowOf(GroupState(loaded = true))
            } else {
                val data = combine(repository.seats(session.id), repository.beers(session.id), repository.water(session.id)) { seats, beers, water ->
                    GroupRules.ordered(seats) to SeatTimeRules.times(seats, beers, water)
                }
                combine(data, groupTimers.running(session.id), store.timerState, enabled) { (seats, times), others, mine, on ->
                    GroupState(true, session, seats, times, timerStarts(session, times, others, mine), on)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupState())

    private fun timerStarts(session: Session, times: List<SeatTimes>, others: Map<Side, SeatTimer>, mine: TimerState): Map<Side, Long> =
        times.mapNotNull { t ->
            if (t.side == session.meSide) {
                mine.startedAt?.takeIf { mine.running && mine.sessionId == session.id && mine.beerId == t.openDrink?.id }?.let { t.side to it }
            } else {
                others[t.side]?.takeIf { SeatTimeRules.isValid(it, t) }?.let { t.side to it.startedAt }
            }
        }.toMap()

    private val added = Channel<BeerEvent>(Channel.CONFLATED)
    val beerAdded: Flow<BeerEvent> = added.receiveAsFlow()
    private val waterAddedChannel = Channel<WaterEvent>(Channel.CONFLATED)
    val waterAdded: Flow<WaterEvent> = waterAddedChannel.receiveAsFlow()

    /** A running timer means that drink is done once the next one comes: finish it and stop the timer. */
    fun add(side: Side, drink: DrinkChoice? = null) = viewModelScope.launch {
        val s = state.value
        val session = s.session ?: return@launch
        val running = side in s.timers
        if (running) dropTimer(session, side)
        repository.addBeer(finishPrevious = running, side = side, drink = drink)?.let { added.send(it) }
    }

    fun removeLatest(side: Side) = viewModelScope.launch { repository.removeLatest(side) }

    fun undo(event: BeerEvent) = viewModelScope.launch { repository.undo(event) }

    fun addWater(side: Side) = viewModelScope.launch { repository.addWater(side)?.let { waterAddedChannel.send(it) } }

    fun undoWater(event: WaterEvent) = viewModelScope.launch { repository.undoWater(event) }

    fun startTimer(side: Side) = viewModelScope.launch {
        val s = state.value
        val session = s.session ?: return@launch
        val times = s.times.firstOrNull { it.side == side } ?: return@launch
        if (side == session.meSide) drinkTimer.start() else groupTimers.start(session.id, times, s.timerEnabled)
        onTimerChanged()
    }

    fun stopTimer(side: Side) = viewModelScope.launch {
        val session = state.value.session ?: return@launch
        if (side == session.meSide) drinkTimer.stop() else groupTimers.stop(session.id, side)
        onTimerChanged()
    }

    private suspend fun dropTimer(session: Session, side: Side) {
        if (side == session.meSide) drinkTimer.clear() else groupTimers.drop(session.id, side)
        onTimerChanged()
    }

    fun endSession() = viewModelScope.launch { repository.endSession() }

    fun save(setup: GroupSetup, start: Boolean) = viewModelScope.launch {
        if (start) repository.startGroup(setup) else repository.updateGroup(setup)
    }

    companion object {
        fun factory(
            repository: SessionRepository,
            store: SettingsStore,
            drinkTimer: DrinkTimer,
            groupTimers: GroupTimers,
            onTimerChanged: suspend () -> Unit,
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { GroupViewModel(repository, store, drinkTimer, groupTimers, onTimerChanged) } }
    }
}

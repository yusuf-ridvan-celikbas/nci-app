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
import com.cansin.app.domain.GroupRules
import com.cansin.app.domain.GroupSetup
import com.cansin.app.domain.SeatTally
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GroupState(
    val loaded: Boolean = false,
    val session: Session? = null,
    val seats: List<GroupSeat> = emptyList(),
    val tally: List<SeatTally> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class GroupViewModel(private val repository: SessionRepository) : ViewModel() {
    val state: StateFlow<GroupState> = repository.openSession
        .flatMapLatest { session ->
            if (session?.mode != SessionMode.GROUP) {
                flowOf(GroupState(loaded = true))
            } else {
                combine(repository.seats(session.id), repository.beers(session.id), repository.water(session.id)) { seats, beers, water ->
                    GroupState(true, session, GroupRules.ordered(seats), GroupRules.tally(seats, beers, water))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupState())

    private val added = Channel<BeerEvent>(Channel.CONFLATED)
    val beerAdded: Flow<BeerEvent> = added.receiveAsFlow()
    private val waterAddedChannel = Channel<WaterEvent>(Channel.CONFLATED)
    val waterAdded: Flow<WaterEvent> = waterAddedChannel.receiveAsFlow()

    fun add(side: Side) = viewModelScope.launch { repository.addBeer(side = side)?.let { added.send(it) } }

    fun removeLatest(side: Side) = viewModelScope.launch { repository.removeLatest(side) }

    fun undo(event: BeerEvent) = viewModelScope.launch { repository.undo(event) }

    fun addWater(side: Side) = viewModelScope.launch { repository.addWater(side)?.let { waterAddedChannel.send(it) } }

    fun undoWater(event: WaterEvent) = viewModelScope.launch { repository.undoWater(event) }

    fun endSession() = viewModelScope.launch { repository.endSession() }

    fun save(setup: GroupSetup, start: Boolean) = viewModelScope.launch {
        if (start) repository.startGroup(setup) else repository.updateGroup(setup)
    }

    companion object {
        fun factory(repository: SessionRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { GroupViewModel(repository) } }
    }
}

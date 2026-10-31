package com.cansin.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cansin.app.data.BeerEvent
import com.cansin.app.data.SessionRepository
import com.cansin.app.domain.BeerSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CounterState(val count: Int = 0, val sessionActive: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class CounterViewModel(private val repository: SessionRepository) : ViewModel() {
    val state: StateFlow<CounterState> = repository.openSession
        .flatMapLatest { session ->
            if (session == null) flowOf(CounterState())
            else repository.beerCount(session.id).map { CounterState(it, sessionActive = true) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CounterState())

    private val added = Channel<BeerEvent>(Channel.CONFLATED)
    val beerAdded: Flow<BeerEvent> = added.receiveAsFlow()

    fun refresh() = viewModelScope.launch { repository.closeIfStale() }

    fun add(size: BeerSize? = null) = viewModelScope.launch { added.send(repository.addBeer(size = size)) }

    fun undo(event: BeerEvent) = viewModelScope.launch { repository.undo(event) }

    fun removeLatest() = viewModelScope.launch { repository.removeLatest() }

    fun endSession() = viewModelScope.launch { repository.endSession() }

    companion object {
        fun factory(repository: SessionRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CounterViewModel(repository) }
        }
    }
}

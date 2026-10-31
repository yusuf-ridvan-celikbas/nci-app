package com.cansin.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cansin.app.data.BeerEvent
import com.cansin.app.data.HistoryRepository
import com.cansin.app.data.Note
import com.cansin.app.data.Session
import com.cansin.app.data.SessionSummary
import com.cansin.app.data.WaterEvent
import com.cansin.app.domain.Overview
import com.cansin.app.domain.SessionStats
import com.cansin.app.domain.StatsRules
import com.cansin.app.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionDetail(
    val session: Session,
    val beers: List<BeerEvent>,
    val notes: List<Note>,
    val water: List<WaterEvent>,
    val stats: SessionStats?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val history: HistoryRepository, settingsStore: SettingsStore) : ViewModel() {
    private val profile = settingsStore.settings.map { it.profile }.distinctUntilChanged()

    val summaries: StateFlow<List<SessionSummary>?> = history.summaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val overview: StateFlow<Overview?> = combine(history.summaries, history.endedBeers, profile) { summaries, beers, p ->
        StatsRules.overview(summaries.map { it.session }, beers, summaries.associate { it.session.id to it.water }, p)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val selected = MutableStateFlow<Long?>(null)

    val detail: StateFlow<SessionDetail?> = selected.flatMapLatest { id ->
        if (id == null) flowOf(null) else detailFlow(id)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(id: Long?) {
        selected.value = id
    }

    fun delete(id: Long) = viewModelScope.launch { history.delete(id) }

    private fun detailFlow(id: Long): Flow<SessionDetail?> = combine(
        history.session(id),
        history.beers(id),
        history.notes(id),
        history.water(id),
        profile,
    ) { session, beers, notes, water, p ->
        session?.let { SessionDetail(it, beers, notes, water, StatsRules.session(it, beers, p)) }
    }

    companion object {
        fun factory(history: HistoryRepository, settingsStore: SettingsStore): ViewModelProvider.Factory =
            viewModelFactory { initializer { HistoryViewModel(history, settingsStore) } }
    }
}

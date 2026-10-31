package com.cansin.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cansin.app.data.BeerEvent
import com.cansin.app.data.SessionRepository
import com.cansin.app.domain.BodyProfile
import com.cansin.app.domain.Drink
import com.cansin.app.domain.PromilleEstimate
import com.cansin.app.domain.PromilleModel
import com.cansin.app.domain.VersusRules
import com.cansin.app.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

data class PromilleState(
    val loaded: Boolean = false,
    val profile: BodyProfile? = null,
    val promptDismissed: Boolean = false,
    val beers: List<BeerEvent> = emptyList(),
    val estimate: PromilleEstimate? = null,
    val now: Long = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PromilleViewModel(
    private val repository: SessionRepository,
    private val settingsStore: SettingsStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val now = MutableStateFlow(clock())

    private val beers = repository.openSession.flatMapLatest { session ->
        if (session == null) flowOf(emptyList()) else repository.beers(session.id).map { list -> list.filter { VersusRules.isMine(it, session) } }
    }

    val state: StateFlow<PromilleState> = combine(
        beers,
        settingsStore.settings.map { it.profile to it.profilePromptDismissed }.distinctUntilChanged(),
        now,
    ) { list, (profile, dismissed), _ ->
        val time = clock()
        val estimate = profile?.takeIf { list.isNotEmpty() }?.let { p ->
            PromilleModel.estimate(p, list.map { Drink(it.at, it.finishedAt, it.sizeMl, it.abv) }, time)
        }
        PromilleState(true, profile, dismissed, list, estimate, time)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PromilleState())

    fun tick() {
        now.value = clock()
    }

    fun updateBeer(event: BeerEvent) = viewModelScope.launch { repository.updateBeer(event) }

    fun saveProfile(profile: BodyProfile?) = viewModelScope.launch { settingsStore.setProfile(profile) }

    fun dismissPrompt() = viewModelScope.launch { settingsStore.dismissProfilePrompt() }

    companion object {
        fun factory(repository: SessionRepository, settingsStore: SettingsStore): ViewModelProvider.Factory =
            viewModelFactory { initializer { PromilleViewModel(repository, settingsStore) } }
    }
}

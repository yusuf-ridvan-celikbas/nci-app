package com.cansin.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cansin.app.data.SessionRepository
import com.cansin.app.domain.CareDismissals
import com.cansin.app.domain.CareInput
import com.cansin.app.domain.CareKind
import com.cansin.app.domain.CareRules
import com.cansin.app.domain.VersusRules
import com.cansin.app.prefs.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId

data class CareState(val kind: CareKind? = null, val home: String? = null, val beerLimit: Int? = null, val budgetMinor: Long? = null)

private data class OpenSession(val active: Boolean = false, val myBeers: Int = 0, val mySpend: Long = 0)

@OptIn(ExperimentalCoroutinesApi::class)
class CareViewModel(repository: SessionRepository, settingsStore: SettingsStore) : ViewModel() {
    private val now = MutableStateFlow(System.currentTimeMillis())
    private val dismissed = MutableStateFlow(CareDismissals())

    private val session = repository.openSession.flatMapLatest { s ->
        if (s == null) {
            flowOf(OpenSession())
        } else {
            combine(repository.beers(s.id), repository.notes(s.id)) { beers, notes ->
                OpenSession(true, beers.count { VersusRules.isMine(it, s) }, CareRules.mySpend(s, com.cansin.app.domain.TabRules.withDrinks(notes, beers, s.mode)))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OpenSession())

    val state: StateFlow<CareState> = combine(session, settingsStore.settings, now, dismissed) { s, settings, t, d ->
        val input = CareInput(
            enabled = settings.careEnabled,
            sessionActive = s.active,
            now = t,
            hour = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).hour,
            lateHour = settings.careLateHour,
            myBeers = s.myBeers,
            beerLimit = settings.beerLimit,
            budgetMinor = settings.budgetMinor,
            mySpendMinor = s.mySpend,
        )
        CareState(CareRules.pick(input, d), settings.homePlace, settings.beerLimit, settings.budgetMinor)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareState())

    fun tick(at: Long) {
        now.value = at
    }

    fun dismiss(kind: CareKind) = dismissed.update {
        val s = session.value
        when (kind) {
            CareKind.LATE -> it.copy(lateAt = System.currentTimeMillis())
            CareKind.SLOW_DOWN -> it.copy(slowDownAtBeers = s.myBeers)
            CareKind.BEER_LIMIT -> it.copy(limitAtBeers = s.myBeers)
            CareKind.BUDGET -> it.copy(budgetAtSpend = s.mySpend)
        }
    }

    companion object {
        fun factory(repository: SessionRepository, settingsStore: SettingsStore): ViewModelProvider.Factory =
            viewModelFactory { initializer { CareViewModel(repository, settingsStore) } }
    }
}

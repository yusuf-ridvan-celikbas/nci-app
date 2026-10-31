package com.cansin.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cansin.app.data.SessionRepository
import com.cansin.app.domain.CareDismissals
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId

data class CareState(val kind: CareKind? = null, val home: String? = null)

private data class OpenSession(val active: Boolean = false, val myBeers: Int = 0)

@OptIn(ExperimentalCoroutinesApi::class)
class CareViewModel(repository: SessionRepository, settingsStore: SettingsStore) : ViewModel() {
    private val now = MutableStateFlow(System.currentTimeMillis())
    private val dismissed = MutableStateFlow(CareDismissals())

    private val session = repository.openSession.flatMapLatest { s ->
        if (s == null) flowOf(OpenSession()) else repository.beers(s.id).map { beers -> OpenSession(true, beers.count { VersusRules.isMine(it, s) }) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OpenSession())

    val state: StateFlow<CareState> = combine(session, settingsStore.settings, now, dismissed) { s, settings, t, d ->
        val hour = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).hour
        CareState(CareRules.pick(settings.careEnabled, s.active, t, hour, settings.careLateHour, s.myBeers, d), settings.homePlace)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareState())

    fun tick(at: Long) {
        now.value = at
    }

    fun dismiss(kind: CareKind) = dismissed.update {
        when (kind) {
            CareKind.LATE -> it.copy(lateAt = System.currentTimeMillis())
            CareKind.SLOW_DOWN -> it.copy(slowDownAtBeers = session.value.myBeers)
        }
    }

    companion object {
        fun factory(repository: SessionRepository, settingsStore: SettingsStore): ViewModelProvider.Factory =
            viewModelFactory { initializer { CareViewModel(repository, settingsStore) } }
    }
}

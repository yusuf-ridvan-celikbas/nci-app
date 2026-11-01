package com.cansin.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cansin.app.data.Note
import com.cansin.app.data.Session
import com.cansin.app.data.SessionMode
import com.cansin.app.data.SessionRepository
import com.cansin.app.data.Side
import com.cansin.app.data.GroupSeat
import com.cansin.app.domain.TabTotals
import kotlinx.coroutines.flow.combine
import com.cansin.app.domain.VersusRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesState(val notes: List<Note> = emptyList(), val session: Session? = null, val seats: List<GroupSeat> = emptyList()) {
    val versus: Boolean get() = session?.mode == SessionMode.VERSUS
    val group: Boolean get() = session?.mode == SessionMode.GROUP
    /** Notes can be put on a side/seat. */
    val sided: Boolean get() = VersusRules.hasSides(session?.mode)
    val totals: TabTotals get() = VersusRules.totals(notes)
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(private val repository: SessionRepository) : ViewModel() {
    val state: StateFlow<NotesState> = repository.openSession
        .flatMapLatest { session ->
            when {
                session == null -> flowOf(NotesState())
                session.mode == SessionMode.GROUP ->
                    combine(repository.notes(session.id), repository.seats(session.id)) { notes, seats -> NotesState(notes, session, seats) }
                else -> repository.notes(session.id).map { NotesState(it, session) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesState())

    fun add(text: String, amountMinor: Long?, side: Side?) = viewModelScope.launch { repository.addNote(text, amountMinor, side) }

    fun update(note: Note) = viewModelScope.launch { repository.updateNote(note) }

    fun delete(note: Note) = viewModelScope.launch { repository.deleteNote(note) }

    companion object {
        fun factory(repository: SessionRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { NotesViewModel(repository) }
        }
    }
}

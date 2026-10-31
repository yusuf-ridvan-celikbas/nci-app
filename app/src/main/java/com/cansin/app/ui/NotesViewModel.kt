package com.cansin.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cansin.app.data.Note
import com.cansin.app.data.SessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesState(val notes: List<Note> = emptyList()) {
    val totalMinor: Long get() = notes.sumOf { it.amountMinor ?: 0 }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(private val repository: SessionRepository) : ViewModel() {
    val state: StateFlow<NotesState> = repository.openSession
        .flatMapLatest { session ->
            if (session == null) flowOf(NotesState()) else repository.notes(session.id).map(::NotesState)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesState())

    fun add(text: String, amountMinor: Long?) = viewModelScope.launch { repository.addNote(text, amountMinor) }

    fun update(note: Note) = viewModelScope.launch { repository.updateNote(note) }

    fun delete(note: Note) = viewModelScope.launch { repository.deleteNote(note) }

    companion object {
        fun factory(repository: SessionRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { NotesViewModel(repository) }
        }
    }
}

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
import com.cansin.app.domain.DrinkLine
import com.cansin.app.domain.TabRules
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

data class NotesState(
    val notes: List<Note> = emptyList(),
    val session: Session? = null,
    val seats: List<GroupSeat> = emptyList(),
    val beers: List<com.cansin.app.data.BeerEvent> = emptyList(),
) {
    val versus: Boolean get() = session?.mode == SessionMode.VERSUS
    val group: Boolean get() = session?.mode == SessionMode.GROUP
    /** Notes can be put on a side/seat. */
    val sided: Boolean get() = VersusRules.hasSides(session?.mode)
    /** P28: notes plus priced drinks; every total and Settle up use this. */
    val tab: List<Note> get() = TabRules.withDrinks(notes, beers, session?.mode)
    val totals: TabTotals get() = VersusRules.totals(tab)
    val drinkLines: List<DrinkLine> get() = TabRules.drinkLines(beers, session?.mode)
    /** Drinks tonight but none has a price: point to Settings → Drinks → Prices (or tap a line). */
    val showPriceHint: Boolean get() = beers.isNotEmpty() && beers.none { (it.priceMinor ?: 0) > 0 }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(private val repository: SessionRepository) : ViewModel() {
    val state: StateFlow<NotesState> = repository.openSession
        .flatMapLatest { session ->
            when {
                session == null -> flowOf(NotesState())
                session.mode == SessionMode.GROUP ->
                    combine(repository.notes(session.id), repository.seats(session.id), repository.beers(session.id)) { notes, seats, beers ->
                        NotesState(notes, session, seats, beers)
                    }
                else -> combine(repository.notes(session.id), repository.beers(session.id)) { notes, beers -> NotesState(notes, session, beers = beers) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesState())

    fun add(text: String, amountMinor: Long?, side: Side?) = viewModelScope.launch { repository.addNote(text, amountMinor, side) }

    fun update(note: Note) = viewModelScope.launch { repository.updateNote(note) }

    fun delete(note: Note) = viewModelScope.launch { repository.deleteNote(note) }

    fun addDrink(line: DrinkLine) = viewModelScope.launch {
        repository.addBeer(side = line.beerSide, drink = com.cansin.app.domain.DrinkChoice(line.kind, com.cansin.app.domain.BeerSize(line.sizeMl, line.abv)))
    }

    fun removeDrink(line: DrinkLine) = viewModelScope.launch { repository.removeLatestDrink(line.kind, line.sizeMl, line.beerSide) }

    fun setPrice(line: DrinkLine, minor: Long?) = viewModelScope.launch { repository.setDrinkPrice(line.kind, line.sizeMl, line.beerSide, minor) }

    companion object {
        fun factory(repository: SessionRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { NotesViewModel(repository) }
        }
    }
}

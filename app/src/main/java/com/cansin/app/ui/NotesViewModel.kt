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
    val water: List<com.cansin.app.data.WaterEvent> = emptyList(),
) {
    val versus: Boolean get() = session?.mode == SessionMode.VERSUS
    val group: Boolean get() = session?.mode == SessionMode.GROUP
    /** Notes can be put on a side/seat. */
    val sided: Boolean get() = VersusRules.hasSides(session?.mode)
    /** P28: notes plus priced drinks; every total and Settle up use this. */
    val tab: List<Note> get() = TabRules.withDrinksAndWater(notes, beers, water, session?.mode)
    val waterLines: List<com.cansin.app.domain.WaterLine> get() = TabRules.waterLines(water)
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
                    combine(repository.notes(session.id), repository.seats(session.id), repository.beers(session.id), repository.water(session.id)) { notes, seats, beers, water ->
                        NotesState(notes, session, seats, beers, water)
                    }
                else -> combine(repository.notes(session.id), repository.beers(session.id), repository.water(session.id)) { notes, beers, water ->
                    NotesState(notes, session, beers = beers, water = water)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesState())

    fun add(text: String, amountMinor: Long?, side: Side?) = viewModelScope.launch { repository.addNote(text, amountMinor, side) }

    fun update(note: Note) = viewModelScope.launch { repository.updateNote(note) }

    fun delete(note: Note) = viewModelScope.launch { repository.deleteNote(note) }

    fun addDrink(line: DrinkLine) = viewModelScope.launch {
        repository.addBeer(side = line.beerSide, drink = com.cansin.app.domain.DrinkChoice(line.kind, com.cansin.app.domain.BeerSize(line.sizeMl, line.abv), line.serving))
    }

    fun removeDrink(line: DrinkLine) = viewModelScope.launch { repository.removeLatestDrink(line.kind, line.sizeMl, line.beerSide, line.serving) }

    fun addWater(line: com.cansin.app.domain.WaterLine) = viewModelScope.launch { repository.addWater(line.side, line.sizeMl) }

    fun removeWater(line: com.cansin.app.domain.WaterLine) = viewModelScope.launch { repository.removeLatestWater(line.side, line.sizeMl) }

    fun setWaterPrice(line: com.cansin.app.domain.WaterLine, minor: Long?) = viewModelScope.launch { repository.setWaterPrice(line.side, line.sizeMl, minor) }

    /** One Save in the line dialog: price (old line), then draft/bottle. */
    fun saveLine(line: DrinkLine, priceMinor: Long?, priceChanged: Boolean, serving: com.cansin.app.data.Serving?) = viewModelScope.launch {
        if (priceChanged) repository.setDrinkPrice(line.kind, line.sizeMl, line.beerSide, priceMinor, line.serving)
        if (serving != line.serving && line.kind == com.cansin.app.data.DrinkKind.BEER) {
            repository.setServing(line.kind, line.sizeMl, line.beerSide, line.serving, serving)
        }
    }

    fun setServing(line: DrinkLine, to: com.cansin.app.data.Serving) =
        viewModelScope.launch { repository.setServing(line.kind, line.sizeMl, line.beerSide, line.serving, to) }

    fun setPrice(line: DrinkLine, minor: Long?) = viewModelScope.launch { repository.setDrinkPrice(line.kind, line.sizeMl, line.beerSide, minor, line.serving) }

    companion object {
        fun factory(repository: SessionRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { NotesViewModel(repository) }
        }
    }
}

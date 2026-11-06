package com.cansin.app.ui

import com.cansin.app.system.PictureStore
import com.cansin.app.domain.PictureRules
import com.cansin.app.domain.PictureChange
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cansin.app.data.BeerEvent
import com.cansin.app.data.FriendProfile
import com.cansin.app.data.FriendRepository
import com.cansin.app.data.Session
import com.cansin.app.data.SessionMode
import com.cansin.app.data.SessionRepository
import com.cansin.app.data.Side
import com.cansin.app.domain.VersusSetup
import com.cansin.app.ui.theme.VersusColors
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

data class VersusState(val loaded: Boolean = false, val session: Session? = null, val red: Int = 0, val blue: Int = 0)

data class SideDraft(
    val name: String = "",
    val friendId: Long? = null,
    val saveAsFriend: Boolean = false,
    val picture: PictureChange = PictureChange.KEEP,
)

@OptIn(ExperimentalCoroutinesApi::class)
class VersusViewModel(
    private val repository: SessionRepository,
    private val friendRepository: FriendRepository,
    private val pictures: PictureStore,
) : ViewModel() {
    val state: StateFlow<VersusState> = repository.openSession
        .flatMapLatest { session ->
            if (session?.mode != SessionMode.VERSUS) {
                flowOf(VersusState(loaded = true))
            } else {
                repository.beers(session.id).map { list ->
                    VersusState(true, session, list.count { it.side == Side.RED }, list.count { it.side == Side.BLUE })
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VersusState())

    val friends: StateFlow<List<FriendProfile>> = friendRepository.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val added = Channel<BeerEvent>(Channel.CONFLATED)
    val beerAdded: Flow<BeerEvent> = added.receiveAsFlow()

    fun add(side: Side) = viewModelScope.launch { repository.addBeer(side = side)?.let { added.send(it) } }

    fun removeLatest(side: Side) = viewModelScope.launch { repository.removeLatest(side) }

    fun undo(event: BeerEvent) = viewModelScope.launch { repository.undo(event) }

    fun endSession() = viewModelScope.launch { repository.endSession() }

    fun save(red: SideDraft, blue: SideDraft, me: Side?, start: Boolean) = viewModelScope.launch {
        val setup = VersusSetup(
            redName = red.name,
            blueName = blue.name,
            redFriendId = link(red, VersusColors.red.toArgb()),
            blueFriendId = link(blue, VersusColors.blue.toArgb()),
            meSide = me,
        )
        val id = if (start) repository.startVersus(setup).id else state.value.session?.id.also { repository.updateVersus(setup) }
        pictures.apply(Side.RED, red.picture, PictureRules.keyFor(id, Side.RED, setup.redFriendId))
        pictures.apply(Side.BLUE, blue.picture, PictureRules.keyFor(id, Side.BLUE, setup.blueFriendId))
    }

    fun deleteFriend(id: Long) = viewModelScope.launch { friendRepository.delete(id) }

    private suspend fun link(draft: SideDraft, color: Int): Long? =
        draft.friendId ?: if (draft.saveAsFriend) friendRepository.save(draft.name, color)?.id else null

    companion object {
        fun factory(repository: SessionRepository, friends: FriendRepository, pictures: PictureStore): ViewModelProvider.Factory =
            viewModelFactory { initializer { VersusViewModel(repository, friends, pictures) } }
    }
}

package com.cansin.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cansin.app.data.CustomQuestion
import com.cansin.app.data.GameRepository
import com.cansin.app.data.QuestionKind
import com.cansin.app.data.Side
import com.cansin.app.domain.GamePack
import com.cansin.app.domain.GameRules
import com.cansin.app.domain.ShuffleBag
import com.cansin.app.domain.VersusRules
import com.cansin.app.domain.QuestionImport
import com.cansin.app.domain.QuestionImportResult
import com.cansin.app.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** [side] = seat colour when the players come from Versus/Together; null = colour by position. */
data class GamePlayer(val name: String, val side: Side? = null)

data class GameCard(val kind: QuestionKind, val text: String)

data class GameState(
    val playing: Boolean = false,
    val players: List<GamePlayer> = emptyList(),
    val pack: GamePack = GamePack.FRIENDS,
    val includeOwn: Boolean = true,
    val includeBuiltIn: Boolean = true,
    /** Set when Truth/Dare had nothing to draw (e.g. built-in off and no own questions of that kind). */
    val empty: QuestionKind? = null,
    val avoidRepeat: Boolean = true,
    /** Who answers (the rim points at them). */
    val turn: Int? = null,
    /** Who asks (the foot points at them). */
    val asker: Int? = null,
    /** Final glass angle; while [spinning], the UI animates from [fromAngle] to it. */
    val angle: Float = 0f,
    val fromAngle: Float = 0f,
    val spinId: Int = 0,
    val spinning: Boolean = false,
    val card: GameCard? = null,
    /** Truth or Dare switched on inside Versus/Together (Versus stays the main view). */
    val shownInSession: Boolean = false,
)

/** Game state lives here (in memory) so switching tabs keeps it. Answers are never stored. */
class GameViewModel(
    private val repository: GameRepository,
    private val store: SettingsStore,
    private val random: Random = Random.Default,
) : ViewModel() {
    private val ui = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = ui.asStateFlow()

    val own: StateFlow<List<CustomQuestion>> = repository.own.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val bags = mutableMapOf<QuestionKind, ShuffleBag>()

    init {
        viewModelScope.launch {
            store.settings.map { it.gameBuiltIn to it.gameOwn }.distinctUntilChanged().collect { (builtIn, mine) ->
                bags.clear()
                ui.update { it.copy(includeBuiltIn = builtIn, includeOwn = mine, empty = null) }
            }
        }
    }

    fun setPack(pack: GamePack) = ui.update { it.copy(pack = pack) }.also { bags.clear() }

    fun setIncludeOwn(on: Boolean) = viewModelScope.launch { store.setGameSources(builtIn = ui.value.includeBuiltIn || !on, own = on) }

    fun setIncludeBuiltIn(on: Boolean) = viewModelScope.launch { store.setGameSources(builtIn = on, own = ui.value.includeOwn || !on) }

    fun setAvoidRepeat(on: Boolean) = ui.update { it.copy(avoidRepeat = on) }

    fun start(players: List<GamePlayer>) {
        val clean = players.mapNotNull { p -> VersusRules.cleanName(p.name)?.let { p.copy(name = it) } }.take(GameRules.MAX_PLAYERS)
        if (clean.size < GameRules.MIN_PLAYERS) return
        bags.clear()
        ui.update { it.copy(playing = true, players = clean, turn = null, asker = null, card = null, spinning = false) }
    }

    /** Versus/Together: play with the session's people; restarts only when they change. */
    fun usePlayers(players: List<GamePlayer>) {
        val s = ui.value
        if (s.playing && s.players == players) return
        start(players)
    }

    fun setShownInSession(on: Boolean) = ui.update { it.copy(shownInSession = on) }

    /** No Versus/Together open any more: a game with that session's people goes back to setup; a hand-typed game keeps going. */
    fun sessionClosed() = ui.update {
        val linked = it.players.any { p -> p.side != null }
        if (linked) it.copy(playing = false, spinning = false, card = null, turn = null, asker = null, shownInSession = false)
        else it.copy(shownInSession = false)
    }

    fun backToSetup() = ui.update { it.copy(playing = false, spinning = false, card = null, turn = null, asker = null) }

    fun spin() {
        val s = ui.value
        if (!s.playing || s.spinning || s.players.size < GameRules.MIN_PLAYERS) return
        val count = s.players.size
        val next = GameRules.pickNext(count, s.turn, s.avoidRepeat, random)
        val asker = GameRules.askerCandidates(next, count).random(random)
        val target = GameRules.spinTarget(s.angle, next, count, tilt = GameRules.askerTilt(next, asker, count))
        ui.update {
            it.copy(fromAngle = it.angle, angle = target, turn = next, asker = asker, spinId = it.spinId + 1, spinning = true, card = null)
        }
    }

    fun spinDone() = ui.update { it.copy(spinning = false) }

    /** [builtIn] comes from the current language's resources; own questions are added when switched on. */
    fun draw(kind: QuestionKind, builtIn: List<String>) {
        val s = ui.value
        if (s.turn == null || s.spinning) return
        val items = (if (s.includeBuiltIn) builtIn else emptyList()) +
            if (s.includeOwn) own.value.filter { it.kind == kind }.map { it.text } else emptyList()
        val bag = bags[kind]?.takeIf { it.source == items } ?: ShuffleBag(items, random).also { bags[kind] = it }
        val text = bag.next()
        ui.update { it.copy(card = text?.let { t -> GameCard(kind, t) }, empty = if (text == null) kind else null) }
    }

    fun addOwn(kind: QuestionKind, text: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(repository.add(kind, text))
        bags.remove(kind)
    }

    /** Parses a picked CSV and adds the good rows; the report says what was skipped and why. */
    fun importOwn(text: String, onResult: (QuestionImportResult, Int) -> Unit) = viewModelScope.launch {
        val current = own.value
        val result = withContext(Dispatchers.Default) {
            QuestionImport.parse(text, current, (CustomQuestion.MAX_COUNT - current.size).coerceAtLeast(0))
        }
        val added = repository.addAll(result.questions)
        bags.clear()
        onResult(result, added)
    }

    fun deleteOwn(id: Long) = viewModelScope.launch { repository.delete(id); bags.clear() }

    companion object {
        fun factory(repository: GameRepository, store: SettingsStore): ViewModelProvider.Factory =
            viewModelFactory { initializer { GameViewModel(repository, store) } }
    }
}

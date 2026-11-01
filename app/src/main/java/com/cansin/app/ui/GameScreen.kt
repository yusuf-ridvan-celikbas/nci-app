package com.cansin.app.ui

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.data.FriendProfile
import com.cansin.app.data.GroupSeat
import com.cansin.app.data.QuestionKind
import com.cansin.app.data.Session
import com.cansin.app.domain.GamePack
import com.cansin.app.domain.GameRules

private val playersSaver = listSaver<SnapshotStateList<String>, String>({ it.toList() }, { mutableStateListOf<String>().apply { addAll(it) } })

@StringRes
internal fun packName(pack: GamePack): Int = when (pack) {
    GamePack.FRIENDS -> R.string.game_pack_friends
    GamePack.BEST_FRIENDS -> R.string.game_pack_best
    GamePack.COUPLES -> R.string.game_pack_couples
}

@ArrayRes
internal fun packArray(pack: GamePack, kind: QuestionKind): Int = when (pack) {
    GamePack.FRIENDS -> if (kind == QuestionKind.TRUTH) R.array.game_friends_truth else R.array.game_friends_dare
    GamePack.BEST_FRIENDS -> if (kind == QuestionKind.TRUTH) R.array.game_best_truth else R.array.game_best_dare
    GamePack.COUPLES -> if (kind == QuestionKind.TRUTH) R.array.game_couples_truth else R.array.game_couples_dare
}

/** P26 Truth or Dare. Players can come from an open Versus/Together session ([session] + [seats]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(viewModel: GameViewModel, session: Session?, seats: List<GroupSeat>) {
    val state by viewModel.state.collectAsState()
    val own by viewModel.own.collectAsState()
    var managing by rememberSaveable { mutableStateOf(false) }
    CompositionLocalProvider(LocalSeats provides seats) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(if (state.playing) packName(state.pack) else R.string.game_title)) },
                    actions = {
                        if (state.playing) TextButton(onClick = viewModel::backToSetup) { Text(stringResource(R.string.game_change_players)) }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.playing) GamePanel(viewModel, state, showPacks = false) else Setup(viewModel, state, own.size, session) { managing = true }
            }
        }
    }
    if (managing) MyQuestionsDialog(viewModel, own, onDismiss = { managing = false })
}

@Composable
private fun Setup(viewModel: GameViewModel, state: GameState, ownCount: Int, session: Session?, onManage: () -> Unit) {
    Text(stringResource(R.string.game_subtitle), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val linked = GameRules.linkedSides(session, LocalSeats.current).map { GamePlayer(sideName(session, it), it) }
    if (linked.size >= GameRules.MIN_PLAYERS) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.game_play_with), style = MaterialTheme.typography.titleMedium)
                Text(linked.joinToString(", ") { it.name })
                Button(onClick = { viewModel.start(linked) }) { Text(stringResource(R.string.game_use_players)) }
            }
        }
    }
    val names = rememberSaveable(saver = playersSaver) { mutableStateListOf("", "") }
    Text(stringResource(R.string.game_players), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
    names.forEachIndexed { i, name ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { names[i] = it.take(FriendProfile.MAX_NAME) },
                label = { Text(stringResource(R.string.game_player_n, i + 1)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (names.size > GameRules.MIN_PLAYERS) {
                IconButton(onClick = { names.removeAt(i) }) {
                    Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.game_remove_player))
                }
            }
        }
    }
    if (names.size < GameRules.MAX_PLAYERS) {
        TextButton(onClick = { names.add("") }) { Text(stringResource(R.string.game_add_player)) }
    }

    Text(stringResource(R.string.game_pack), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GamePack.entries.forEach { pack ->
            FilterChip(
                selected = pack == state.pack,
                onClick = { viewModel.setPack(pack) },
                label = { Text(stringResource(packName(pack))) },
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
    SwitchRow(R.string.game_include_own, state.includeOwn, viewModel::setIncludeOwn)
    OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.game_my_questions, ownCount)) }
    SwitchRow(R.string.game_avoid_repeat, state.avoidRepeat, viewModel::setAvoidRepeat)
    Text(
        stringResource(R.string.game_skip_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val players = GameRules.cleanPlayers(names)
    Button(
        onClick = { viewModel.start(players.map { GamePlayer(it) }) },
        enabled = GameRules.canStart(players),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text(stringResource(R.string.game_start)) }
}

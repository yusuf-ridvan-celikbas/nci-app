package com.cansin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.data.QuestionKind
import com.cansin.app.data.Session
import com.cansin.app.domain.GamePack
import com.cansin.app.domain.GameRules

/** Top-bar switch in Versus/Together: shows Truth or Dare on the same screen. */
@Composable
fun GameToggle(on: Boolean, onToggle: (Boolean) -> Unit) {
    IconToggleButton(checked = on, onCheckedChange = onToggle) {
        Icon(
            painterResource(R.drawable.ic_game),
            stringResource(if (on) R.string.game_hide else R.string.game_show),
            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Keeps the game's players equal to the open session's sides/seats (names and colours). */
@Composable
fun SyncGamePlayers(game: GameViewModel, session: Session, active: Boolean) {
    val players = GameRules.linkedSides(session, LocalSeats.current).map { GamePlayer(sideName(session, it), it) }
    LaunchedEffect(players, active) { if (active) game.usePlayers(players) }
}

@Composable
fun PackChips(selected: GamePack, onSelect: (GamePack) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GamePack.entries.forEach { pack ->
            FilterChip(
                selected = pack == selected,
                onClick = { onSelect(pack) },
                label = { Text(stringResource(packName(pack))) },
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
}

/** One compact line per side/person while the game is shown: colour dot, name, count, −1/+1 (and an optional extra button). */
@Composable
fun CompactSideRow(
    name: String,
    colors: Pair<Color, Color>,
    count: Int,
    addDescription: String,
    removeDescription: String,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(14.dp).background(colors.first, CircleShape))
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(count.toString(), style = MaterialTheme.typography.titleLarge)
        OutlinedButton(
            onClick = onRemove,
            enabled = count > 0,
            modifier = Modifier.semantics { contentDescription = removeDescription },
        ) { Text(stringResource(R.string.minus_one)) }
        Button(
            onClick = onAdd,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = colors.first, contentColor = colors.second),
            modifier = Modifier.semantics { contentDescription = addDescription },
        ) { Text(stringResource(R.string.plus_one)) }
        extra()
    }
}

/** The playing part of Truth or Dare: glass, whose turn, Truth/Dare, card, Skip/Next. Shared by the Game tab and Versus/Together. */
@Composable
fun GamePanel(viewModel: GameViewModel, state: GameState, showPacks: Boolean) {
    val truths = stringArrayResource(packArray(state.pack, QuestionKind.TRUTH)).toList()
    val dares = stringArrayResource(packArray(state.pack, QuestionKind.DARE)).toList()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showPacks) PackChips(state.pack, viewModel::setPack)
        if (state.players.size < GameRules.MIN_PLAYERS) return@Column
        SpinGlass(state, onSpin = viewModel::spin, onSpinDone = viewModel::spinDone)
        val turn = state.turn?.let { state.players.getOrNull(it) }
        Text(
            when {
                state.spinning -> stringResource(R.string.game_spinning)
                turn != null -> stringResource(R.string.game_turn, turn.name)
                else -> stringResource(R.string.game_tap_to_spin)
            },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        val card = state.card
        when {
            state.spinning -> Unit
            turn == null -> Button(onClick = viewModel::spin, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.game_spin))
            }
            card == null -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { viewModel.draw(QuestionKind.TRUTH, truths) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.game_truth))
                }
                FilledTonalButton(onClick = { viewModel.draw(QuestionKind.DARE, dares) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.game_dare))
                }
            }
            else -> {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(if (card.kind == QuestionKind.TRUTH) R.string.game_truth else R.string.game_dare),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(card.text, style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.draw(card.kind, if (card.kind == QuestionKind.TRUTH) truths else dares) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.game_skip)) }
                    Button(onClick = viewModel::spin, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.game_next)) }
                }
            }
        }
    }
}

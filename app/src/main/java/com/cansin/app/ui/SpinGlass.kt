package com.cansin.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.domain.GameRules
import com.cansin.app.domain.GroupRules
import com.cansin.app.ui.theme.seatColors
import kotlin.math.cos
import kotlin.math.sin

private const val SPIN_MS = 2_800
private val CHIP_MAX = 104.dp
/** Narrower name chips from 7 players up so ten still fit around the glass. */
private val CHIP_MAX_CROWDED = 84.dp
private const val CROWDED = 7
private val GLASS_BOX = 150.dp

/** Seat colour of a player: from Versus/Together, else by position (cycling the seat colours). */
@Composable
fun playerColors(player: GamePlayer, index: Int): Pair<Color, Color> =
    sideColor(player.side) ?: seatColors(GroupRules.SIDES[index % GroupRules.SIDES.size])
        ?: (MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary)

/** Names around a circle with a wine glass in the middle; tap the glass to spin. */
@Composable
fun SpinGlass(state: GameState, onSpin: () -> Unit, onSpinDone: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val anim = remember { Animatable(if (state.spinning) state.fromAngle else state.angle) }
    LaunchedEffect(state.spinId) {
        if (state.spinning) {
            anim.snapTo(state.fromAngle)
            anim.animateTo(state.angle, tween(SPIN_MS, easing = FastOutSlowInEasing))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onSpinDone()
        } else {
            anim.snapTo(state.angle)
        }
    }
    val count = state.players.size
    val spinLabel = stringResource(R.string.game_spin_description)
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp)) {
        val chipMax = if (count >= CROWDED) CHIP_MAX_CROWDED else CHIP_MAX
        val radius = (maxWidth / 2) - (chipMax / 2) + 8.dp
        state.players.forEachIndexed { i, player ->
            val theta = Math.toRadians(GameRules.angleFor(i, count).toDouble())
            val settled = !state.spinning
            val answers = settled && state.turn == i
            val asks = settled && state.asker == i
            val (bg, fg) = playerColors(player, i)
            val colors = MaterialTheme.colorScheme
            val label = when {
                answers -> stringResource(R.string.game_chosen, player.name)
                asks -> stringResource(R.string.game_asker_desc, player.name)
                else -> null
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = when {
                    answers -> bg
                    asks -> colors.secondaryContainer
                    else -> colors.surfaceContainerHigh
                },
                contentColor = when {
                    answers -> fg
                    asks -> colors.onSecondaryContainer
                    else -> colors.onSurface
                },
                border = BorderStroke(if (answers || asks) 3.dp else 2.dp, if (asks) colors.secondary else bg),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = radius * sin(theta).toFloat(), y = -radius * cos(theta).toFloat())
                    .widthIn(max = chipMax)
                    .then(if (label != null) Modifier.semantics(mergeDescendants = true) { contentDescription = label } else Modifier),
            ) {
                Column(Modifier.padding(horizontal = if (count >= CROWDED) 8.dp else 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        player.name,
                        style = if (answers || asks) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (answers || asks) {
                        Text(
                            stringResource(if (answers) R.string.game_answerer_tag else R.string.game_asker_tag),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        val pointer = MaterialTheme.colorScheme.primary
        val foot = MaterialTheme.colorScheme.secondary
        Box(
            Modifier
                .align(Alignment.Center)
                .size(GLASS_BOX)
                .rotate(anim.value)
                .clip(CircleShape)
                .clickable(enabled = !state.spinning, role = Role.Button, onClick = onSpin)
                .semantics { contentDescription = spinLabel },
        ) {
            Canvas(Modifier.align(Alignment.TopCenter).size(22.dp)) {
                val p = Path().apply {
                    moveTo(size.width / 2, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(p, pointer)
            }
            // Foot of the glass: points at who asks.
            Canvas(Modifier.align(Alignment.BottomCenter).size(18.dp)) { drawCircle(foot) }
            Icon(
                painterResource(R.drawable.ic_game),
                contentDescription = null,
                tint = pointer,
                modifier = Modifier.align(Alignment.Center).size(96.dp),
            )
        }
    }
}

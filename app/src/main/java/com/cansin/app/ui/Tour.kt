package com.cansin.app.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cansin.app.R
import com.cansin.app.domain.TourStep
import com.cansin.app.domain.TutorialRules
import com.cansin.app.domain.TutorialTopic
import kotlinx.coroutines.delay

/** Where each tour target currently is on screen (root coordinates); filled by [tourTarget]. */
class TourTargets {
    internal val bounds = mutableStateMapOf<TourStep, Rect>()

    fun present(): Set<TourStep> = bounds.filterValues { it.width > 0f && it.height > 0f }.keys
}

val LocalTourTargets = staticCompositionLocalOf { TourTargets() }

/** Marks a button/area as a spotlight stop. Off-screen or hidden = empty bounds = skipped. */
fun Modifier.tourTarget(step: TourStep): Modifier = composed {
    val targets = LocalTourTargets.current
    DisposableEffect(step, targets) { onDispose { targets.bounds.remove(step) } }
    onGloballyPositioned { coords ->
        val rect = coords.boundsInRoot()
        if (targets.bounds[step] != rect) targets.bounds[step] = rect
    }
}

private const val SETTLE_MS = 350L

/**
 * Spotlight tour over the real screen: everything is dimmed except one button, with a bubble explaining it.
 * Taps never reach the app; tap anywhere or Next to go on. Skip, Done and Back close it.
 */
@Composable
fun TourOverlay(topic: TutorialTopic, onClose: () -> Unit) {
    val targets = LocalTourTargets.current
    var steps by remember(topic) { mutableStateOf<List<TourStep>?>(null) }
    var index by remember(topic) { mutableIntStateOf(0) }
    LaunchedEffect(topic) {
        delay(SETTLE_MS) // let the screen finish opening and laying out
        steps = TutorialRules.tour(topic, targets.present())
    }
    val list = steps ?: return
    BackHandler(onBack = onClose)
    if (list.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val step = list[index.coerceAtMost(list.lastIndex)]
    val rect = targets.bounds[step]?.takeIf { it.width > 0f && it.height > 0f }
    val last = index >= list.lastIndex
    val next: () -> Unit = { if (last) onClose() else index++ }
    if (rect == null) {
        // The button went away (scrolled, state changed): move on.
        LaunchedEffect(step) { next() }
        return
    }

    val density = LocalDensity.current
    val pad = with(density) { 6.dp.toPx() }
    val hole = Rect(rect.left - pad, rect.top - pad, rect.right + pad, rect.bottom + pad)
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f)
    val ring = MaterialTheme.colorScheme.primary
    val corner = CornerRadius(with(density) { 16.dp.toPx() })
    val stroke = with(density) { 3.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawBehind {
                drawRect(scrim)
                drawRoundRect(Color.Transparent, hole.topLeft, hole.size, corner, blendMode = BlendMode.Clear)
                drawRoundRect(ring, hole.topLeft, hole.size, corner, style = Stroke(stroke))
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = next),
    ) {
        BubbleLayout(hole) {
            ElevatedCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(stepTitle(step)), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(stepBody(step)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (!last) TextButton(onClick = onClose) { Text(stringResource(R.string.tut_skip)) }
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.tut_page, index + 1, list.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = next) { Text(stringResource(if (last) R.string.tut_done else R.string.tut_next)) }
                    }
                }
            }
        }
    }
}

/** Puts the bubble below the hole when there is room, else above it; always inside the safe area. */
@Composable
private fun BubbleLayout(hole: Rect, content: @Composable () -> Unit) {
    val insets = WindowInsets.safeDrawing
    val density = LocalDensity.current
    Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
        val margin = 16.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val maxWidth = (constraints.maxWidth - 2 * margin).coerceAtMost(420.dp.roundToPx())
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, maxWidth = maxWidth, minHeight = 0))
        val top = insets.getTop(density) + margin
        val bottom = constraints.maxHeight - insets.getBottom(density) - margin
        val below = hole.bottom.toInt() + gap
        val above = hole.top.toInt() - gap - placeable.height
        val y = when {
            below + placeable.height <= bottom -> below
            above >= top -> above
            else -> (bottom - placeable.height).coerceAtLeast(top)
        }
        val x = (hole.center.x.toInt() - placeable.width / 2).coerceIn(margin, constraints.maxWidth - margin - placeable.width)
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
    }
}

@StringRes
private fun stepTitle(step: TourStep): Int = when (step) {
    TourStep.TONIGHT_PLUS -> R.string.tour_tonight_plus_title
    TourStep.TONIGHT_DRINKS -> R.string.tour_tonight_drinks_title
    TourStep.TONIGHT_TIMER -> R.string.tour_tonight_timer_title
    TourStep.TONIGHT_WATER -> R.string.tour_tonight_water_title
    TourStep.TONIGHT_PROMILLE -> R.string.tour_tonight_promille_title
    TourStep.TONIGHT_FRIENDS -> R.string.tour_tonight_friends_title
    TourStep.TONIGHT_END -> R.string.tour_tonight_end_title
    TourStep.TONIGHT_NAV -> R.string.tour_tonight_nav_title
    TourStep.TAB_DRINKS -> R.string.tour_tab_drinks_title
    TourStep.TAB_ADD -> R.string.tour_tab_add_title
    TourStep.TAB_TOTAL -> R.string.tour_tab_total_title
    TourStep.TAB_SETTLE -> R.string.tour_tab_settle_title
    TourStep.GAME_LINKED -> R.string.tour_game_linked_title
    TourStep.GAME_PLAYERS -> R.string.tour_game_players_title
    TourStep.GAME_PACKS -> R.string.tour_game_packs_title
    TourStep.GAME_MINE -> R.string.tour_game_mine_title
    TourStep.GAME_START -> R.string.tour_game_start_title
    TourStep.GAME_GLASS -> R.string.tour_game_glass_title
    TourStep.GAME_ACTIONS -> R.string.tour_game_actions_title
    TourStep.GAME_CHANGE -> R.string.tour_game_change_title
    TourStep.HISTORY_TABS -> R.string.tour_history_tabs_title
    TourStep.HISTORY_FILTER -> R.string.tour_history_filter_title
    TourStep.HISTORY_LIST -> R.string.tour_history_list_title
    TourStep.HISTORY_SELECT -> R.string.tour_history_select_title
    TourStep.VS_PAGES -> R.string.tour_vs_pages_title
    TourStep.VS_SIDES -> R.string.tour_vs_sides_title
    TourStep.GROUP_SEATS -> R.string.tour_group_seats_title
    TourStep.GROUP_LAYOUT -> R.string.tour_group_layout_title
    TourStep.GROUP_GAME -> R.string.tour_group_game_title
    TourStep.FRIENDS_EDIT -> R.string.tour_friends_edit_title
    TourStep.FRIENDS_PIN -> R.string.tour_friends_pin_title
    TourStep.FRIENDS_END -> R.string.tour_friends_end_title
}

@StringRes
private fun stepBody(step: TourStep): Int = when (step) {
    TourStep.TONIGHT_PLUS -> R.string.tour_tonight_plus_body
    TourStep.TONIGHT_DRINKS -> R.string.tour_tonight_drinks_body
    TourStep.TONIGHT_TIMER -> R.string.tour_tonight_timer_body
    TourStep.TONIGHT_WATER -> R.string.tour_tonight_water_body
    TourStep.TONIGHT_PROMILLE -> R.string.tour_tonight_promille_body
    TourStep.TONIGHT_FRIENDS -> R.string.tour_tonight_friends_body
    TourStep.TONIGHT_END -> R.string.tour_tonight_end_body
    TourStep.TONIGHT_NAV -> R.string.tour_tonight_nav_body
    TourStep.TAB_DRINKS -> R.string.tour_tab_drinks_body
    TourStep.TAB_ADD -> R.string.tour_tab_add_body
    TourStep.TAB_TOTAL -> R.string.tour_tab_total_body
    TourStep.TAB_SETTLE -> R.string.tour_tab_settle_body
    TourStep.GAME_LINKED -> R.string.tour_game_linked_body
    TourStep.GAME_PLAYERS -> R.string.tour_game_players_body
    TourStep.GAME_PACKS -> R.string.tour_game_packs_body
    TourStep.GAME_MINE -> R.string.tour_game_mine_body
    TourStep.GAME_START -> R.string.tour_game_start_body
    TourStep.GAME_GLASS -> R.string.tour_game_glass_body
    TourStep.GAME_ACTIONS -> R.string.tour_game_actions_body
    TourStep.GAME_CHANGE -> R.string.tour_game_change_body
    TourStep.HISTORY_TABS -> R.string.tour_history_tabs_body
    TourStep.HISTORY_FILTER -> R.string.tour_history_filter_body
    TourStep.HISTORY_LIST -> R.string.tour_history_list_body
    TourStep.HISTORY_SELECT -> R.string.tour_history_select_body
    TourStep.VS_PAGES -> R.string.tour_vs_pages_body
    TourStep.VS_SIDES -> R.string.tour_vs_sides_body
    TourStep.GROUP_SEATS -> R.string.tour_group_seats_body
    TourStep.GROUP_LAYOUT -> R.string.tour_group_layout_body
    TourStep.GROUP_GAME -> R.string.tour_group_game_body
    TourStep.FRIENDS_EDIT -> R.string.tour_friends_edit_body
    TourStep.FRIENDS_PIN -> R.string.tour_friends_pin_body
    TourStep.FRIENDS_END -> R.string.tour_friends_end_body
}

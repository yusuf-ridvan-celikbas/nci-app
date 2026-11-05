package com.cansin.app.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cansin.app.R

private const val ENTER_MS = 260
private const val STAGGER_MS = 45

/** Blur for the screen behind the Game menu (API 31+; older phones get the dim layer only). */
fun Modifier.behindGameMenu(open: Boolean): Modifier =
    if (open && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blur(14.dp) else this

/**
 * Game tab tap: three floating cards rise from the Game tab over a dimmed (and blurred) screen.
 * Soy tu Tentación, Versus, Together. Tap outside or Back closes it.
 */
@Composable
fun GameMenu(open: Boolean, onTruthOrDare: () -> Unit, onVersus: () -> Unit, onTogether: () -> Unit, onDismiss: () -> Unit) {
    BackHandler(open, onDismiss)
    fun pick(action: () -> Unit) {
        onDismiss()
        action()
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(open, enter = fadeIn(tween(ENTER_MS)), exit = fadeOut(tween(ENTER_MS))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 16.dp).widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top card is the last to rise, so the menu "grows" up from the tab.
            MenuCard(open, order = 2, R.drawable.ic_game, { GameTitle() }, R.string.game_pick_tod_desc) { pick(onTruthOrDare) }
            MenuCard(open, order = 1, R.drawable.ic_versus, { Text(stringResource(R.string.mode_versus)) }, R.string.game_pick_versus_desc) { pick(onVersus) }
            MenuCard(open, order = 0, R.drawable.ic_versus, { Text(stringResource(R.string.mode_group)) }, R.string.game_pick_group_desc) { pick(onTogether) }
        }
    }
}

@Composable
private fun MenuCard(
    open: Boolean,
    order: Int,
    @DrawableRes icon: Int,
    title: @Composable () -> Unit,
    @StringRes description: Int,
    onClick: () -> Unit,
) {
    val delay = order * STAGGER_MS
    AnimatedVisibility(
        open,
        enter = fadeIn(tween(ENTER_MS, delay)) +
            slideInVertically(tween(ENTER_MS, delay, FastOutSlowInEasing)) { it * (order + 1) } +
            scaleIn(tween(ENTER_MS, delay), initialScale = 0.85f, transformOrigin = TransformOrigin(0.5f, 1f)),
        exit = fadeOut(tween(ENTER_MS / 2)) +
            slideOutVertically(tween(ENTER_MS / 2)) { it / 2 } +
            scaleOut(tween(ENTER_MS / 2), targetScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 1f)),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium) { title() }
                    Text(
                        stringResource(description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

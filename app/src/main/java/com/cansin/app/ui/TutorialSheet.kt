package com.cansin.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cansin.app.R
import com.cansin.app.domain.TutorialTopic
import kotlinx.coroutines.launch

/** Opens a guide; provided by MainActivity so screens only need [HelpButton]. */
val LocalTutorial = compositionLocalOf<(TutorialTopic) -> Unit> { {} }

private class TutorialPage(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val body: Int)

@StringRes
fun tutorialTitle(topic: TutorialTopic): Int = when (topic) {
    TutorialTopic.GENERAL -> R.string.tut_topic_general
    TutorialTopic.TONIGHT -> R.string.tut_topic_tonight
    TutorialTopic.TAB -> R.string.tut_topic_tab
    TutorialTopic.GAME -> R.string.tut_topic_game
    TutorialTopic.HISTORY -> R.string.tut_topic_history
    TutorialTopic.FRIENDS -> R.string.tut_topic_friends
    TutorialTopic.WIDGET -> R.string.tut_topic_widget
}

private fun pages(topic: TutorialTopic): List<TutorialPage> = when (topic) {
    TutorialTopic.GENERAL -> listOf(
        TutorialPage(R.drawable.ic_shortcut_beer, R.string.tut_general_1_title, R.string.tut_general_1_body),
        TutorialPage(R.drawable.ic_shortcut_beer, R.string.tut_general_2_title, R.string.tut_general_2_body),
        TutorialPage(R.drawable.ic_shortcut_water, R.string.tut_general_3_title, R.string.tut_general_3_body),
        TutorialPage(R.drawable.ic_receipt, R.string.tut_general_4_title, R.string.tut_general_4_body),
        TutorialPage(R.drawable.ic_versus, R.string.tut_general_5_title, R.string.tut_general_5_body),
        TutorialPage(R.drawable.ic_lock, R.string.tut_general_6_title, R.string.tut_general_6_body),
    )
    TutorialTopic.WIDGET -> listOf(
        TutorialPage(R.drawable.ic_shortcut_beer, R.string.tut_widget_1_title, R.string.tut_widget_1_body),
        TutorialPage(R.drawable.ic_shortcut_water, R.string.tut_widget_2_title, R.string.tut_widget_2_body),
        TutorialPage(R.drawable.ic_lock, R.string.tut_widget_3_title, R.string.tut_widget_3_body),
    )
    // Screens get a spotlight tour (Tour.kt) instead of cards.
    else -> emptyList()
}

/** "?" for a top bar: opens this screen's guide. */
@Composable
fun HelpButton(topic: TutorialTopic) {
    val open = LocalTutorial.current
    IconButton(onClick = { open(topic) }) {
        Icon(painterResource(R.drawable.ic_help), stringResource(R.string.tut_help))
    }
}

/** Full-screen swipe cards for GENERAL and WIDGET. Skip, Done and Back all close it (and count as seen). */
@Composable
fun TutorialSheet(topic: TutorialTopic, onClose: () -> Unit) {
    val list = pages(topic)
    val pager = rememberPagerState { list.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == list.lastIndex
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                Text(
                    stringResource(tutorialTitle(topic)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
                    val page = list[i]
                    val position = stringResource(R.string.tut_page, i + 1, list.size)
                    Column(
                        Modifier.fillMaxSize().padding(24.dp).semantics(mergeDescendants = true) { stateDescription = position },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier.size(120.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(page.icon),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(56.dp),
                            )
                        }
                        Spacer(Modifier.size(32.dp))
                        Text(stringResource(page.title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            stringResource(page.body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Dots(list.size, pager.currentPage)
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!last) TextButton(onClick = onClose) { Text(stringResource(R.string.tut_skip)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { if (last) onClose() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                        Text(stringResource(if (last) R.string.tut_done else R.string.tut_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun Dots(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        repeat(count) { i ->
            val color = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(Modifier.size(8.dp).background(color, CircleShape))
        }
    }
}

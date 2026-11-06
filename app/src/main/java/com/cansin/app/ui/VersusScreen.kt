package com.cansin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cansin.app.R
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import com.cansin.app.ui.theme.PictureColors
import com.cansin.app.domain.PictureRules
import com.cansin.app.domain.TourStep
import com.cansin.app.domain.TutorialTopic
import com.cansin.app.data.Session
import com.cansin.app.data.Side

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersusScreen(
    viewModel: VersusViewModel,
    game: GameViewModel,
    onEditSides: () -> Unit,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    promilleCard: @Composable () -> Unit,
    waterBar: @Composable (SnackbarHostState) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val session = state.session ?: return
    val snackbar = remember { SnackbarHostState() }
    UndoEffect(viewModel.beerAdded, snackbar, viewModel::undo)
    val gameState by game.state.collectAsState()
    val gameOn = gameState.shownInSession
    SyncGamePlayers(game, session, gameOn)
    val pager = rememberPagerState(initialPage = if (gameOn) PAGE_GAME else PAGE_SIDES) { 3 }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.settledPage) { game.setShownInSession(pager.settledPage == PAGE_GAME) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.versus_title)) },
                actions = {
                    HelpButton(TutorialTopic.FRIENDS)
                    IconButton(onClick = onTogglePin, modifier = Modifier.tourTarget(TourStep.FRIENDS_PIN)) {
                        Icon(
                            painterResource(if (pinned) R.drawable.ic_lock else R.drawable.ic_lock_open),
                            stringResource(if (pinned) R.string.unpin_screen else R.string.pin_screen),
                        )
                    }
                    IconButton(onClick = onEditSides, modifier = Modifier.tourTarget(TourStep.FRIENDS_EDIT)) {
                        Icon(painterResource(R.drawable.ic_versus), stringResource(R.string.versus_edit))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            OutlinedButton(
                onClick = { viewModel.endSession() },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).tourTarget(TourStep.FRIENDS_END),
            ) { Text(stringResource(R.string.end_session)) }
        },
    ) { padding ->
        // Three pages: the sides alone, then water + ‰, then the game. The game page open = game "shown in session".
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = pager.currentPage, modifier = Modifier.tourTarget(TourStep.VS_PAGES)) {
                listOf(R.string.versus_page_sides, R.string.versus_page_water, R.string.nav_game).forEachIndexed { i, label ->
                    Tab(
                        selected = pager.currentPage == i,
                        onClick = { scope.launch { pager.animateScrollToPage(i) } },
                        text = { Text(stringResource(label), maxLines = 1) },
                    )
                }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                when (page) {
                    PAGE_SIDES -> Column(Modifier.fillMaxSize()) {
                        SideHalf(session, Side.RED, state.red, onEditSides, viewModel::add, viewModel::removeLatest)
                        SideHalf(session, Side.BLUE, state.blue, onEditSides, viewModel::add, viewModel::removeLatest)
                    }
                    PAGE_WATER -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        waterBar(snackbar)
                        promilleCard()
                    }
                    else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        GamePanel(game, gameState, showPacks = true)
                    }
                }
            }
        }
    }
}

private const val PAGE_SIDES = 0
private const val PAGE_WATER = 1
private const val PAGE_GAME = 2

@Composable
private fun ColumnScope.SideHalf(
    session: Session,
    side: Side,
    count: Int,
    onEditSides: () -> Unit,
    onAdd: (Side) -> Unit,
    onRemove: (Side) -> Unit,
) {
    val (bg, fg) = sideColor(side) ?: (Color.Unspecified to Color.Unspecified)
    val name = sideName(session, side)
    val addDescription = stringResource(R.string.versus_add, name)
    val removeDescription = stringResource(R.string.versus_remove, name)
    Column(
        Modifier.fillMaxWidth().weight(1f).then(if (side == Side.RED) Modifier.tourTarget(TourStep.VS_SIDES) else Modifier).background(bg).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            if (session.meSide == side) stringResource(R.string.versus_name_me, name) else name,
            color = fg,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clickable(onClick = onEditSides),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            TextButton(
                onClick = { onRemove(side) },
                enabled = count > 0,
                colors = ButtonDefaults.textButtonColors(contentColor = fg, disabledContentColor = fg.copy(alpha = 0.5f)),
                modifier = Modifier.semantics { contentDescription = removeDescription },
            ) { Text(stringResource(R.string.minus_one), fontSize = 24.sp) }
            Text(count.toString(), color = fg, style = MaterialTheme.typography.displayLarge)
            val friendId = if (side == Side.RED) session.redFriendId else session.blueFriendId
            val picture = rememberPicture(PictureRules.keyFor(session.id, side, friendId))
            Button(
                onClick = { onAdd(side) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(88.dp).semantics { contentDescription = addDescription },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    picture?.let { PictureFill(it) }
                    Text(stringResource(R.string.plus_one), fontSize = 28.sp, color = if (picture != null) PictureColors.onPicture else bg)
                }
            }
        }
    }
}

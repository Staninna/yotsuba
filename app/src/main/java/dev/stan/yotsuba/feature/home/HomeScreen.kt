package dev.stan.yotsuba.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.EmptyState
import dev.stan.yotsuba.core.designsystem.component.LoadingSkeleton
import dev.stan.yotsuba.core.designsystem.component.TabChrome
import dev.stan.yotsuba.core.designsystem.component.TabScaffoldSlots
import dev.stan.yotsuba.core.designsystem.component.showUndo
import dev.stan.yotsuba.core.designsystem.rememberHaptics
import dev.stan.yotsuba.core.designsystem.rememberMotionSpec
import dev.stan.yotsuba.core.designsystem.token.LocalMotion
import dev.stan.yotsuba.feature.catalog.CatalogActions
import dev.stan.yotsuba.feature.catalog.CatalogPane
import dev.stan.yotsuba.feature.catalog.CatalogViewModel
import dev.stan.yotsuba.feature.catalog.catalogViewModel
import kotlinx.coroutines.launch

/**
 * The Home tab: a pager over the user's favourite boards, each page a full catalog. The
 * current page survives tab switches and process death via [rememberSaveable]; every page's
 * list state lives in its own [CatalogPane] and ViewModel, keyed by board.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    slots: TabScaffoldSlots,
    onOpenThread: (board: String, threadNo: Long) -> Unit,
    onOpenBoards: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val boards by viewModel.boards.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pageCount = boards?.size ?: 0
    val pagerState = rememberPagerState(pageCount = { pageCount })
    // The pager is created before the boards have loaded, when pageCount is still 0, so the
    // saved page cannot go through initialPage. Restore it once the list is known, and only
    // write back after that so the restored value is not clobbered by the initial page 0.
    var savedPage by rememberSaveable { mutableIntStateOf(0) }
    var restored by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pageCount) {
        if (!restored && pageCount > 0) {
            pagerState.scrollToPage(savedPage.coerceIn(0, pageCount - 1))
            restored = true
        }
    }
    LaunchedEffect(pagerState.currentPage) { if (restored) savedPage = pagerState.currentPage }
    val current = boards?.getOrNull(pagerState.currentPage)
    val reorder = rememberTabReorderState()
    val removedTemplate = stringResource(R.string.home_board_removed)
    val undoLabel = stringResource(R.string.action_undo)
    // Resolved here, under Home's own ViewModel store, not inside the shell-owned top bar.
    val currentViewModel = current?.let { catalogViewModel(it) }

    val list = boards
    val nothingToRoll = stringResource(R.string.home_roulette_empty)
    var rolling by remember { mutableStateOf(false) }
    val vetoed by viewModel.vetoedBoards.collectAsStateWithLifecycle()
    TabChrome(
        slots = slots,
        topBar = {
            HomeTopBar(current, currentViewModel, onOpenSettings) {
                if (!list.isNullOrEmpty()) {
                    RouletteAction(
                        boards = list,
                        vetoed = vetoed,
                        enabled = !rolling,
                        onToggleVeto = viewModel::toggleVeto,
                        onRoll = {
                            scope.launch {
                                rolling = true
                                try {
                                    val thread = viewModel.rollThread()
                                    if (thread != null) onOpenThread(thread.board, thread.no)
                                    else slots.snackbar.showSnackbar(nothingToRoll)
                                } finally {
                                    rolling = false
                                }
                            }
                        },
                    )
                }
            }
        },
    )
    when {
        list == null -> LoadingSkeleton()
        list.isEmpty() -> EmptyState(
            title = stringResource(R.string.home_empty_title),
            explanation = stringResource(R.string.home_empty_explanation),
            icon = Icons.Filled.Star,
            action = {
                Button(onClick = onOpenBoards) { Text(stringResource(R.string.home_pick_boards)) }
            },
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        )
        else -> Column(Modifier.fillMaxSize()) {
            ReorderableTabRow(
                boards = list,
                selectedIndex = pagerState.currentPage.coerceIn(0, list.size - 1),
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                onRemove = { index ->
                    val board = list[index]
                    val undo = viewModel.removeFavourite(board)
                    scope.launch { slots.snackbar.showUndo(removedTemplate.format(board), undoLabel, undo) }
                },
                onMove = { from, to ->
                    viewModel.reorder(from, to)
                    scope.launch { pagerState.scrollToPage(remapPage(pagerState.currentPage, from, to)) }
                },
                trailing = {
                    Tab(
                        selected = false,
                        onClick = onOpenBoards,
                        icon = { Icon(Icons.Filled.Add, stringResource(R.string.home_add_board)) },
                    )
                },
                state = reorder,
            )
            RemoveDropZone(visible = reorder.isDragging, active = reorder.overRemove)
            HorizontalPager(
                state = pagerState,
                key = { list[it] },
                beyondViewportPageCount = 0,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val board = list[page]
                CatalogPane(
                    board = board,
                    viewModel = catalogViewModel(board),
                    onOpenThread = { onOpenThread(board, it) },
                    snackbar = slots.snackbar,
                )
            }
        }
    }
}

/**
 * The Home app bar: the current board's title and catalog actions when there is a current
 * page, the plain Home title before the boards have loaded, and Settings in every state.
 * [extraActions] go between the catalog's and Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    board: String?,
    viewModel: CatalogViewModel?,
    onOpenSettings: () -> Unit,
    extraActions: @Composable () -> Unit,
) {
    TopAppBar(
        title = {
            val info = viewModel?.boardInfo?.collectAsStateWithLifecycle()?.value
            Text(info?.title ?: board?.let { "/$it/" } ?: stringResource(R.string.home_title))
        },
        actions = {
            if (viewModel != null) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                CatalogActions(state, viewModel)
            }
            extraActions()
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, stringResource(R.string.home_settings))
            }
        },
    )
}

/**
 * The dice: a tap opens a random thread, a long press lists the favourite boards with a tick
 * for each one the dice may land on. IconButton has no long press, so this is a clipped box
 * with the same footprint and the button role.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RouletteAction(
    boards: List<String>,
    vetoed: Set<String>,
    enabled: Boolean,
    onToggleVeto: (String) -> Unit,
    onRoll: () -> Unit,
) {
    val haptics = rememberHaptics()
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .size(40.dp)
                .clip(CircleShape)
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onRoll,
                    onLongClick = { haptics.longPress(); menuOpen = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Casino,
                stringResource(R.string.home_roulette),
                tint = LocalContentColor.current.copy(alpha = if (enabled) 1f else 0.38f),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            Text(
                stringResource(R.string.home_roulette_boards),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            for (board in boards) {
                DropdownMenuItem(
                    text = { Text("/$board/") },
                    leadingIcon = { Checkbox(checked = board !in vetoed, onCheckedChange = null) },
                    onClick = { onToggleVeto(board) },
                )
            }
        }
    }
}

/** The strip a dragged tab can be dropped on to unfavourite its board. */
@Composable
private fun RemoveDropZone(visible: Boolean, active: Boolean) {
    val motion = LocalMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(rememberMotionSpec(motion.short)) + fadeIn(rememberMotionSpec(motion.short)),
        exit = shrinkVertically(rememberMotionSpec(motion.short)) + fadeOut(rememberMotionSpec(motion.short)),
    ) {
        val background by animateColorAsState(
            targetValue = if (active) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
            animationSpec = rememberMotionSpec(motion.short),
            label = "removeZone",
        )
        val content = if (active) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(56.dp).background(background),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, tint = content)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.home_remove_zone), style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

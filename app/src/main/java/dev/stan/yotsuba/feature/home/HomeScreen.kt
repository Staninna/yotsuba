package dev.stan.yotsuba.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.EmptyState
import dev.stan.yotsuba.core.designsystem.component.showUndo
import dev.stan.yotsuba.core.designsystem.rememberMotionSpec
import dev.stan.yotsuba.core.designsystem.token.LocalMotion
import dev.stan.yotsuba.feature.catalog.CatalogActions
import dev.stan.yotsuba.feature.catalog.CatalogPane
import dev.stan.yotsuba.feature.catalog.catalogViewModel
import kotlinx.coroutines.launch

/**
 * The Home tab: a pager over the user's favourite boards, each page a full catalog. The
 * current page survives tab switches and process death via [rememberSaveable]; every page's
 * list state lives in its own [CatalogPane] and ViewModel, keyed by board.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenThread: (board: String, threadNo: Long) -> Unit,
    onOpenBoards: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val boards by viewModel.boards.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
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
    var draggingTab by remember { mutableStateOf(false) }
    var overRemove by remember { mutableStateOf(false) }
    val removedTemplate = stringResource(R.string.home_board_removed)
    val undoLabel = stringResource(R.string.action_undo)
    val currentViewModel = current?.let { catalogViewModel(it) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            val boardInfo by (currentViewModel?.boardInfo ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) })
                .collectAsStateWithLifecycle()
            TopAppBar(
                title = { Text(boardInfo?.title ?: current?.let { "/$it/" } ?: stringResource(R.string.home_title)) },
                actions = {
                    if (currentViewModel != null) {
                        val state by currentViewModel.uiState.collectAsStateWithLifecycle()
                        CatalogActions(state, currentViewModel)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.home_settings))
                    }
                },
            )
        },
    ) { padding ->
        val list = boards
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(
                title = stringResource(R.string.home_empty_title),
                explanation = stringResource(R.string.home_empty_explanation),
                icon = Icons.Filled.Star,
                action = {
                    Button(onClick = onOpenBoards) { Text(stringResource(R.string.home_pick_boards)) }
                },
                modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
            )
            else -> Column(Modifier.padding(padding).fillMaxSize()) {
                ReorderableTabRow(
                    boards = list,
                    selectedIndex = pagerState.currentPage.coerceIn(0, list.size - 1),
                    onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    onRemove = { index ->
                        val board = list[index]
                        val undo = viewModel.removeFavourite(board)
                        scope.launch { snackbar.showUndo(removedTemplate.format(board), undoLabel, undo) }
                    },
                    onDragState = { dragging, over ->
                        draggingTab = dragging
                        overRemove = over
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
                )
                RemoveDropZone(visible = draggingTab, active = overRemove)
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
                        snackbar = snackbar,
                    )
                }
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

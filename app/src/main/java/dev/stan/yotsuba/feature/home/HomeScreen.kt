package dev.stan.yotsuba.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.EmptyState
import dev.stan.yotsuba.feature.catalog.CatalogActions
import dev.stan.yotsuba.feature.catalog.CatalogPane
import dev.stan.yotsuba.feature.catalog.catalogViewModel
import kotlinx.coroutines.launch

/**
 * The Home tab: a pager over the user's favourite boards, each page a full catalog. The
 * current page survives tab switches via [rememberSaveable]; every page's list state lives
 * in its own [CatalogPane] and ViewModel, keyed by board.
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
    var savedPage by rememberSaveable { mutableIntStateOf(0) }
    val pageCount = boards?.size ?: 0
    val pagerState = rememberPagerState(
        initialPage = savedPage.coerceIn(0, maxOf(pageCount - 1, 0)),
        pageCount = { pageCount },
    )
    LaunchedEffect(pagerState.currentPage) { savedPage = pagerState.currentPage }
    val current = boards?.getOrNull(pagerState.currentPage)
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

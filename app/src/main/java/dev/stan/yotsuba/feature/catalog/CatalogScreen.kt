package dev.stan.yotsuba.feature.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.EmptyState
import dev.stan.yotsuba.core.designsystem.component.NoSearchResults
import dev.stan.yotsuba.core.designsystem.component.SearchField
import dev.stan.yotsuba.core.designsystem.component.UiStateContent
import dev.stan.yotsuba.core.designsystem.component.showUndo
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.core.designsystem.component.MediaThumbnail
import dev.stan.yotsuba.core.designsystem.component.OfflineBanner
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.TimeFormat
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.CatalogThread
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CatalogScreen(
    board: String,
    initialSearch: String?,
    onBack: () -> Unit,
    onOpenThread: (Long) -> Unit,
    viewModel: CatalogViewModel = hiltViewModel<CatalogViewModel, CatalogViewModel.Factory>(
        creationCallback = { it.create(board, initialSearch) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val boardInfo by viewModel.boardInfo.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val showScrollTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 8 }
    }
    val hiddenMessage = stringResource(R.string.catalog_thread_hidden)
    val undoLabel = stringResource(R.string.action_undo)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(boardInfo?.title ?: board)
                        Text(
                            "/$board/",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    val searchOpen = (state as? UiState.Success)?.data?.searchQuery != null
                    IconButton(onClick = { if (searchOpen) viewModel.onCloseSearch() else viewModel.onOpenSearch() }) {
                        Icon(
                            if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                            stringResource(R.string.action_search),
                        )
                    }
                    IconButton(onClick = viewModel::onCycleLayout) {
                        Icon(
                            when ((state as? UiState.Success)?.data?.layout) {
                                CatalogLayout.COMPACT -> Icons.Filled.GridView
                                CatalogLayout.LIST -> Icons.AutoMirrored.Filled.ViewList
                                CatalogLayout.COMFORTABLE, null -> Icons.Filled.ViewAgenda
                            },
                            stringResource(R.string.catalog_layout_switch),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (showScrollTop) {
                FloatingActionButton(onClick = { scope.launch { gridState.animateScrollToItem(0) } }) {
                    Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.catalog_scroll_to_top))
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            UiStateContent(state, onRetry = viewModel::retry) { s ->
                if (s.offline) {
                    OfflineBanner(cachedAtLabel = null, onRetry = { viewModel.load(forceRefresh = true) })
                }
                if (s.searchQuery != null) {
                    SearchField(
                        value = s.searchQuery,
                        onValueChange = viewModel::onSearchChange,
                        hintRes = R.string.catalog_search_hint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.lg, vertical = spacing.sm),
                    )
                }
                PullToRefreshBox(
                    isRefreshing = s.refreshing,
                    onRefresh = { viewModel.load(forceRefresh = true) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        s.threads.isNotEmpty() -> LazyVerticalGrid(
                            state = gridState,
                            columns = when (s.layout) {
                                CatalogLayout.COMFORTABLE -> GridCells.Adaptive(260.dp)
                                CatalogLayout.COMPACT -> GridCells.Adaptive(150.dp)
                                CatalogLayout.LIST -> GridCells.Fixed(1)
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing.md),
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                            verticalArrangement = Arrangement.spacedBy(spacing.md),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(s.threads.size, key = { s.threads[it].no }) { i ->
                                val thread = s.threads[i]
                                ThreadCard(
                                    thread = thread,
                                    layout = s.layout,
                                    onClick = { onOpenThread(thread.no) },
                                    onLongClick = {
                                        viewModel.onHideThread(thread.no)
                                        scope.launch {
                                            snackbar.showUndo(hiddenMessage, undoLabel) { viewModel.onUndoHide(thread.no) }
                                        }
                                    },
                                )
                            }
                        }
                        !s.searchQuery.isNullOrBlank() -> NoSearchResults(
                            s.searchQuery,
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        )
                        else -> EmptyState(
                            title = stringResource(R.string.catalog_empty_title),
                            explanation = stringResource(R.string.catalog_empty_explanation),
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadCard(
    thread: CatalogThread,
    layout: CatalogLayout,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Card(modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        when (layout) {
            CatalogLayout.LIST -> Row(Modifier.padding(spacing.md)) {
                MediaThumbnail(
                    url = thread.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )
                Spacer(Modifier.width(spacing.md))
                Column(Modifier.weight(1f)) {
                    TitleAndBadges(thread)
                    Text(
                        thread.excerpt.plainText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MetadataRow(thread)
                }
            }
            CatalogLayout.COMPACT -> Column {
                if (thread.thumbnailUrl != null) {
                    MediaThumbnail(
                        url = thread.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.2f),
                    )
                }
                Column(Modifier.padding(spacing.sm)) {
                    TitleAndBadges(thread, maxLines = 2)
                    MetadataRow(thread)
                }
            }
            CatalogLayout.COMFORTABLE -> Column {
                if (thread.thumbnailUrl != null) {
                    MediaThumbnail(
                        url = thread.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                    )
                }
                Column(Modifier.padding(spacing.md)) {
                    TitleAndBadges(thread)
                    Text(
                        thread.excerpt.plainText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    MetadataRow(thread)
                }
            }
        }
    }
}

@Composable
private fun TitleAndBadges(thread: CatalogThread, maxLines: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (thread.sticky) {
            Icon(
                Icons.Filled.PushPin,
                contentDescription = stringResource(R.string.catalog_sticky),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (thread.closed) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = stringResource(R.string.catalog_closed),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            thread.displayTitle,
            style = MaterialTheme.typography.titleMedium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MetadataRow(thread: CatalogThread) {
    Text(
        listOf(
            pluralStringResource(R.plurals.replies_count, thread.replyCount, thread.replyCount),
            pluralStringResource(R.plurals.images_count, thread.imageCount, thread.imageCount),
            TimeFormat.relative(thread.lastModified),
        ).joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

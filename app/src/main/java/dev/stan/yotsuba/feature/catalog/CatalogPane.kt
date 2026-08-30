package dev.stan.yotsuba.feature.catalog

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.animatedGridItem
import dev.stan.yotsuba.core.designsystem.component.EmptyState
import dev.stan.yotsuba.core.designsystem.component.MediaThumbnail
import dev.stan.yotsuba.core.designsystem.component.NoSearchResults
import dev.stan.yotsuba.core.designsystem.component.OfflineBanner
import dev.stan.yotsuba.core.designsystem.component.SearchField
import dev.stan.yotsuba.core.designsystem.component.UiStateContent
import dev.stan.yotsuba.core.designsystem.component.sharedMedia
import dev.stan.yotsuba.core.designsystem.component.showUndo
import dev.stan.yotsuba.core.designsystem.motionEnter
import dev.stan.yotsuba.core.designsystem.motionExit
import dev.stan.yotsuba.core.designsystem.rememberHaptics
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.TimeFormat
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.Filter
import kotlinx.coroutines.launch

/**
 * One [CatalogViewModel] per board within the current ViewModelStoreOwner. The key is what
 * lets two panes for two boards on the same screen get two instances instead of sharing one.
 */
@Composable
fun catalogViewModel(board: String, initialSearch: String? = null): CatalogViewModel =
    hiltViewModel<CatalogViewModel, CatalogViewModel.Factory>(
        key = "catalog:$board",
        creationCallback = { it.create(board, initialSearch) },
    )

/**
 * The catalog body: layout modes, search field, hidden and filtered threads, pull-to-refresh
 * and the scroll-to-top button. No Scaffold or top bar, so it can sit inside a pager as well
 * as inside [CatalogScreen]. Hide-undo snackbars go to [snackbar]; pass the host's state when
 * the caller already has a Scaffold.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CatalogPane(
    board: String,
    viewModel: CatalogViewModel,
    onOpenThread: (Long) -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val haptics = rememberHaptics()
    val showScrollTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 8 }
    }
    val hiddenMessage = stringResource(R.string.catalog_thread_hidden)
    val undoLabel = stringResource(R.string.action_undo)
    val linkCopiedMessage = stringResource(R.string.catalog_thread_link_copied)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var sheetThread by remember { mutableStateOf<CatalogThread?>(null) }
    /** Stubbed threads the user tapped open; reset when the pane goes away. */
    var expandedStubs by remember { mutableStateOf(emptySet<Long>()) }

    sheetThread?.let { thread ->
        ThreadActionsSheet(
            thread = thread,
            onDismiss = { sheetThread = null },
            onHide = {
                sheetThread = null
                viewModel.onHideThread(thread.no)
                scope.launch {
                    snackbar.showUndo(hiddenMessage, undoLabel) { viewModel.onUndoHide(thread.no) }
                }
            },
            onCopyLink = {
                sheetThread = null
                clipboard.setText(AnnotatedString(Urls.threadWebUrl(thread.board, thread.no)))
                scope.launch { snackbar.showSnackbar(linkCopiedMessage) }
            },
            onOpenInBrowser = {
                sheetThread = null
                val url = Urls.threadWebUrl(thread.board, thread.no)
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            },
        )
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            UiStateContent(state, onRetry = viewModel::retry) { s ->
                if (s.offline) {
                    OfflineBanner(cachedAtLabel = null, onRetry = { viewModel.load(forceRefresh = true) })
                }
                if (s.searchQuery != null) {
                    // The field only exists while search is open, so entering composition is
                    // the reveal: put the caret in it so the keyboard comes up on the first tap.
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    SearchField(
                        value = s.searchQuery,
                        onValueChange = viewModel::onSearchChange,
                        hintRes = R.string.catalog_search_hint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.lg, vertical = spacing.sm)
                            .focusRequester(focus),
                    )
                }
                PullToRefreshBox(
                    isRefreshing = s.refreshing,
                    onRefresh = { haptics.tick(); viewModel.load(forceRefresh = true) },
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
                            contentPadding = PaddingValues(spacing.md),
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                            verticalArrangement = Arrangement.spacedBy(spacing.md),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // A collapsed stub and a card are different shapes; keying the
                            // slot type keeps the grid from reusing one for the other.
                            fun stubbed(thread: CatalogThread) =
                                thread.no in s.stubs && thread.no !in expandedStubs
                            items(
                                count = s.threads.size,
                                key = { s.threads[it].no },
                                contentType = { if (stubbed(s.threads[it])) "stub" else "thread" },
                            ) { i ->
                                val thread = s.threads[i]
                                Box(animatedGridItem()) {
                                    if (stubbed(thread)) {
                                        FilteredStub(
                                            s.stubs.getValue(thread.no),
                                            onClick = { expandedStubs = expandedStubs + thread.no },
                                        )
                                    } else {
                                        ThreadCard(
                                            thread = thread,
                                            layout = s.layout,
                                            onClick = { onOpenThread(thread.no) },
                                            onLongClick = { haptics.longPress(); sheetThread = thread },
                                        )
                                    }
                                }
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
        AnimatedVisibility(
            visible = showScrollTop,
            enter = motionEnter(),
            exit = motionExit(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(spacing.lg),
        ) {
            FloatingActionButton(onClick = { scope.launch { gridState.animateScrollToItem(0) } }) {
                Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.catalog_scroll_to_top))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadActionsSheet(
    thread: CatalogThread,
    onDismiss: () -> Unit,
    onHide: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    val spacing = LocalSpacing.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            thread.displayTitle,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.catalog_hide_thread)) },
            leadingContent = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onHide),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.catalog_thread_copy_link)) },
            leadingContent = { Icon(Icons.Filled.Link, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onCopyLink),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.catalog_thread_open_in_browser)) },
            leadingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onOpenInBrowser),
        )
        Spacer(Modifier.height(spacing.lg))
    }
}

/** One compact line standing in for a thread a Stub filter caught; tapping shows the real card. */
@Composable
private fun FilteredStub(filter: Filter, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm),
        ) {
            Icon(
                Icons.Filled.FilterAlt,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(spacing.sm))
            Text(
                stringResource(R.string.filters_stub_label, filter.pattern),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    // The OP card in the thread carries the same key, so opening the thread carries the image.
    val shared = thread.thumbnailUrl?.let { Modifier.sharedMedia(it) } ?: Modifier
    Card(modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        when (layout) {
            CatalogLayout.LIST -> Row(Modifier.padding(spacing.md)) {
                MediaThumbnail(
                    url = thread.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp).then(shared),
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
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.2f).then(shared),
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
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f).then(shared),
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

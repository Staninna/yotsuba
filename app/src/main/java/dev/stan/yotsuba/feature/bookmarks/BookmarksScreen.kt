package dev.stan.yotsuba.feature.bookmarks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.EmptyState
import dev.stan.yotsuba.core.designsystem.component.OnResumeEffect
import dev.stan.yotsuba.core.designsystem.component.SwipeToDeleteRow
import dev.stan.yotsuba.core.designsystem.component.ThreadSummaryRow
import dev.stan.yotsuba.core.designsystem.component.showUndo
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onOpenThread: (String, Long) -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedMessage = stringResource(R.string.bookmarks_removed)
    val undoLabel = stringResource(R.string.action_undo)

    // Auto-refresh whenever the tab comes back on screen (throttled in the ViewModel),
    // so the pills update without a manual pull.
    OnResumeEffect(viewModel::onScreenVisible)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.tab_bookmarks))
                        state.checking?.let { (current, total) ->
                            if (total > 0) {
                                Text(
                                    stringResource(R.string.bookmarks_checking, current, total),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                actions = {
                    BookmarksMenu(
                        sortOrder = state.sortOrder,
                        hasDead = state.hasDead,
                        onSortOrderChanged = viewModel::onSortOrderChanged,
                        onRemoveDead = viewModel::onRemoveDead,
                    )
                },
            )
        },
    ) { padding ->
        if (state.loaded && state.bookmarks.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.bookmarks_empty_title),
                explanation = stringResource(R.string.bookmarks_empty_explanation),
                icon = Icons.Filled.BookmarkBorder,
                modifier = Modifier.padding(padding),
            )
        } else {
            PullToRefreshBox(
                isRefreshing = state.checking != null,
                onRefresh = viewModel::onRefreshAll,
                modifier = Modifier.padding(padding),
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        state.bookmarks.size,
                        key = { state.bookmarks[it].board + "/" + state.bookmarks[it].threadNo },
                    ) { i ->
                        val bookmark = state.bookmarks[i]
                        SwipeToDeleteRow(onDelete = {
                            viewModel.onRemove(bookmark)
                            scope.launch {
                                snackbar.showUndo(removedMessage, undoLabel) { viewModel.onUndoRemove(bookmark) }
                            }
                        }) {
                            BookmarkCard(
                                bookmark,
                                onClick = { onOpenThread(bookmark.board, bookmark.threadNo) },
                                onTogglePinned = { viewModel.onTogglePinned(bookmark) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarksMenu(
    sortOrder: BookmarkSortOrder,
    hasDead: Boolean,
    onSortOrderChanged: (BookmarkSortOrder) -> Unit,
    onRemoveDead: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.bookmarks_menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortItem(R.string.bookmarks_sort_unread_first, BookmarkSortOrder.UNREAD_FIRST, sortOrder) {
                open = false; onSortOrderChanged(it)
            }
            SortItem(R.string.bookmarks_sort_last_activity, BookmarkSortOrder.LAST_ACTIVITY, sortOrder) {
                open = false; onSortOrderChanged(it)
            }
            SortItem(R.string.bookmarks_sort_bookmarked, BookmarkSortOrder.BOOKMARKED, sortOrder) {
                open = false; onSortOrderChanged(it)
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.bookmarks_remove_dead)) },
                leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                enabled = hasDead,
                onClick = { open = false; onRemoveDead() },
            )
        }
    }
}

@Composable
private fun SortItem(
    labelRes: Int,
    order: BookmarkSortOrder,
    current: BookmarkSortOrder,
    onSelect: (BookmarkSortOrder) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = {
            if (order == current) Icon(Icons.Filled.Check, contentDescription = null)
            else Box(Modifier.size(24.dp))
        },
        onClick = { onSelect(order) },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkCard(bookmark: Bookmark, onClick: () -> Unit, onTogglePinned: () -> Unit) {
    val spacing = LocalSpacing.current
    val live = bookmark.isLive
    val pinLabel = stringResource(if (bookmark.pinned) R.string.bookmarks_unpin else R.string.bookmarks_pin)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onTogglePinned, onLongClickLabel = pinLabel),
    ) {
        ThreadSummaryRow(
            thumbnailUrl = bookmark.thumbnailUrl,
            title = bookmark.displayTitle,
            excerpt = bookmark.opExcerpt,
            metadata = listOf(
                "/${bookmark.board}/",
                pluralStringResource(R.plurals.replies_count, bookmark.replyCount, bookmark.replyCount),
                pluralStringResource(R.plurals.images_count, bookmark.imageCount, bookmark.imageCount),
            ).joinToString(" · "),
            titleColor = if (live) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (bookmark.pinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = stringResource(R.string.bookmarks_pinned_a11y),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (bookmark.unread > 0) {
                    val a11y = pluralStringResource(R.plurals.bookmarks_unread_a11y, bookmark.unread, bookmark.unread)
                    Text(
                        stringResource(R.string.bookmarks_unread_pill, bookmark.unread),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .semantics { contentDescription = a11y }
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = spacing.sm, vertical = 2.dp),
                    )
                }
                when (bookmark.state) {
                    BookmarkState.ARCHIVED -> StateBadge(stringResource(R.string.bookmarks_badge_archived))
                    BookmarkState.DEAD -> StateBadge(stringResource(R.string.bookmarks_badge_pruned))
                    BookmarkState.ALIVE, BookmarkState.UNKNOWN -> Unit
                }
            }
        }
    }
}

/** A label, not a control: nothing happens when it's tapped, so it isn't a chip. */
@Composable
private fun StateBadge(label: String) {
    val spacing = LocalSpacing.current
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .padding(horizontal = spacing.sm, vertical = 2.dp),
    )
}

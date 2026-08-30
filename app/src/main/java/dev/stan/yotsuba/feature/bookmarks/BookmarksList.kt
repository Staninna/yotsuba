package dev.stan.yotsuba.feature.bookmarks

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.animatedListItem
import dev.stan.yotsuba.core.designsystem.component.EmptyState
import dev.stan.yotsuba.core.designsystem.component.IconMenuItem
import dev.stan.yotsuba.core.designsystem.component.LoadingSkeleton
import dev.stan.yotsuba.core.designsystem.component.OnResumeEffect
import dev.stan.yotsuba.core.designsystem.component.SheetActionRow
import dev.stan.yotsuba.core.designsystem.component.SwipeToDeleteRow
import dev.stan.yotsuba.core.designsystem.component.ThreadSummaryRow
import dev.stan.yotsuba.core.designsystem.component.showUndo
import dev.stan.yotsuba.core.designsystem.labelRes
import dev.stan.yotsuba.core.designsystem.rememberCountTransition
import dev.stan.yotsuba.core.designsystem.rememberHaptics
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkSortOrder
import dev.stan.yotsuba.domain.model.BookmarkState
import kotlinx.coroutines.launch

/**
 * The Watched segment of the Threads tab. The top-bar menu lives in [BookmarksMenu] so the
 * host can put it in whichever app bar it owns; this is only the list. State and callbacks
 * are hoisted: the host already collects the ViewModel for its app bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksList(
    state: BookmarksUiState,
    snapshotResult: SnapshotResult?,
    onSnapshotResultShown: () -> Unit,
    onScreenVisible: () -> Unit,
    onRefreshAll: () -> Unit,
    onRemove: (Bookmark) -> Unit,
    onUndoRemove: (Bookmark) -> Unit,
    onTogglePinned: (Bookmark) -> Unit,
    onSnapshot: (Bookmark) -> Unit,
    onOpenThread: (String, Long) -> Unit,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val removedMessage = stringResource(R.string.bookmarks_removed)
    val undoLabel = stringResource(R.string.action_undo)
    val snapshotMessage = when (snapshotResult) {
        null -> null
        SnapshotResult.Saved -> stringResource(R.string.bookmarks_snapshot_saved)
        is SnapshotResult.Failed -> stringResource(snapshotResult.error.labelRes)
    }
    var sheetFor by remember { mutableStateOf<Bookmark?>(null) }
    val removeWithUndo: (Bookmark) -> Unit = { bookmark ->
        onRemove(bookmark)
        scope.launch {
            snackbar.showUndo(removedMessage, undoLabel) { onUndoRemove(bookmark) }
        }
    }

    // The result is held in the ViewModel until shown, so a snapshot finishing while this
    // segment is off screen still gets its snackbar when the user comes back.
    LaunchedEffect(snapshotResult) {
        snackbar.showSnackbar(snapshotMessage ?: return@LaunchedEffect)
        onSnapshotResultShown()
    }

    // Auto-refresh whenever the tab comes back on screen (throttled in the ViewModel),
    // so the pills update without a manual pull.
    OnResumeEffect(onScreenVisible)

    when {
        !state.loaded -> LoadingSkeleton(modifier)
        state.bookmarks.isEmpty() -> EmptyState(
            title = stringResource(R.string.bookmarks_empty_title),
            explanation = stringResource(R.string.bookmarks_empty_explanation),
            icon = Icons.Filled.BookmarkBorder,
            modifier = modifier,
        )
        else -> PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { haptics.tick(); onRefreshAll() },
            modifier = modifier,
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
                    SwipeToDeleteRow(modifier = animatedListItem(), onDelete = { removeWithUndo(bookmark) }) {
                        BookmarkCard(
                            bookmark,
                            onClick = { onOpenThread(bookmark.board, bookmark.threadNo) },
                            snapshotting = state.isSnapshotting(bookmark),
                            onLongClick = { haptics.longPress(); sheetFor = bookmark },
                        )
                    }
                }
            }
        }
    }

    sheetFor?.let { bookmark ->
        BookmarkActionSheet(
            bookmark = bookmark,
            snapshotting = state.isSnapshotting(bookmark),
            onDismiss = { sheetFor = null },
            onOpen = { sheetFor = null; onOpenThread(bookmark.board, bookmark.threadNo) },
            onTogglePinned = { sheetFor = null; onTogglePinned(bookmark) },
            onSnapshot = { sheetFor = null; onSnapshot(bookmark) },
            onRemove = { sheetFor = null; removeWithUndo(bookmark) },
        )
    }
}

/** Long-press sheet for one row. Snapshot is greyed out on dead rows: only the vault copy remains. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkActionSheet(
    bookmark: Bookmark,
    snapshotting: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onTogglePinned: () -> Unit,
    onSnapshot: () -> Unit,
    onRemove: () -> Unit,
) {
    val spacing = LocalSpacing.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = spacing.xl)) {
            // Not SheetTitle: a thread subject can run long and this one clamps to two lines.
            Text(
                bookmark.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.sm),
            )
            SheetActionRow(stringResource(R.string.bookmarks_open), Icons.Filled.OpenInNew, onOpen)
            SheetActionRow(
                stringResource(if (bookmark.pinned) R.string.bookmarks_unpin else R.string.bookmarks_pin),
                if (bookmark.pinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                onTogglePinned,
            )
            SheetActionRow(
                stringResource(R.string.bookmarks_snapshot),
                Icons.Filled.Inventory2,
                onSnapshot,
                enabled = bookmark.isLive && !snapshotting,
                supporting = when {
                    !bookmark.isLive -> stringResource(R.string.bookmarks_snapshot_dead_hint)
                    snapshotting -> stringResource(R.string.bookmarks_snapshotting)
                    else -> null
                },
            )
            SheetActionRow(stringResource(R.string.bookmarks_remove), Icons.Filled.Delete, onRemove)
        }
    }
}

/** "Checking 3/12" under the title while a refresh runs; nothing otherwise. */
@Composable
fun BookmarksCheckingSubtitle(checking: RefreshProgress?) {
    val progress = checking ?: return
    Text(
        stringResource(R.string.bookmarks_checking, progress.done, progress.total),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun BookmarksMenu(
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
            IconMenuItem(R.string.bookmarks_remove_dead, Icons.Filled.DeleteSweep, enabled = hasDead) {
                open = false; onRemoveDead()
            }
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
private fun BookmarkCard(
    bookmark: Bookmark,
    onClick: () -> Unit,
    snapshotting: Boolean,
    onLongClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val live = bookmark.isLive
    val menuLabel = stringResource(R.string.bookmarks_row_menu)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = menuLabel),
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
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                if (snapshotting) {
                    val a11y = stringResource(R.string.bookmarks_snapshotting)
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp).semantics { contentDescription = a11y },
                    )
                }
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
                    AnimatedContent(
                        targetState = bookmark.unread,
                        transitionSpec = rememberCountTransition(),
                        label = "unread",
                        modifier = Modifier
                            .semantics { contentDescription = a11y }
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = spacing.sm, vertical = 2.dp),
                    ) { unread ->
                        Text(
                            stringResource(R.string.bookmarks_unread_pill, unread),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
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

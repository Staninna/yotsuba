package dev.stan.yotsuba.feature.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
            TopAppBar(title = {
                Column {
                    Text(stringResource(R.string.tab_bookmarks))
                    state.checking?.let { (current, total) ->
                        Text(
                            stringResource(R.string.bookmarks_checking, current, total),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            })
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing.md),
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
                            BookmarkCard(bookmark, onClick = { onOpenThread(bookmark.board, bookmark.threadNo) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(bookmark: Bookmark, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    val dead = bookmark.state == BookmarkState.DEAD
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ThreadSummaryRow(
            thumbnailUrl = bookmark.thumbnailUrl,
            title = bookmark.displayTitle,
            excerpt = bookmark.opExcerpt,
            metadata = listOf(
                "/${bookmark.board}/",
                pluralStringResource(R.plurals.replies_count, bookmark.replyCount, bookmark.replyCount),
                pluralStringResource(R.plurals.images_count, bookmark.imageCount, bookmark.imageCount),
            ).joinToString(" · "),
            titleColor = if (dead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
        ) {
            if (!dead && (bookmark.newReplies > 0 || bookmark.unreadCount > 0)) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (bookmark.newReplies > 0) {
                        val a11y = pluralStringResource(
                            R.plurals.bookmarks_new_replies_a11y, bookmark.newReplies, bookmark.newReplies,
                        )
                        Text(
                            stringResource(R.string.bookmarks_new_replies, bookmark.newReplies),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .semantics { contentDescription = a11y }
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .padding(horizontal = spacing.sm, vertical = 2.dp),
                        )
                    }
                    // Total not-yet-read (past the reading position); hidden when it adds
                    // nothing beyond the "+new" pill.
                    if (bookmark.unreadCount > bookmark.newReplies) {
                        Text(
                            stringResource(R.string.bookmarks_unread, bookmark.unreadCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                .padding(horizontal = spacing.sm, vertical = 2.dp),
                        )
                    }
                }
            }
            if (dead) {
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.bookmarks_archived_badge)) })
            }
        }
    }
}

package dev.stan.yotsuba.feature.threads

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.TabChrome
import dev.stan.yotsuba.core.designsystem.component.TabScaffoldSlots
import dev.stan.yotsuba.core.designsystem.rememberMotionSpec
import dev.stan.yotsuba.core.designsystem.token.LocalMotion
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.feature.bookmarks.BookmarksCheckingSubtitle
import dev.stan.yotsuba.feature.bookmarks.BookmarksList
import dev.stan.yotsuba.feature.bookmarks.BookmarksMenu
import dev.stan.yotsuba.feature.bookmarks.BookmarksViewModel
import dev.stan.yotsuba.feature.history.HistoryClearDialog
import dev.stan.yotsuba.feature.history.HistoryList
import dev.stan.yotsuba.feature.history.HistorySearchField
import dev.stan.yotsuba.feature.history.HistoryViewModel
import kotlinx.coroutines.launch

/** The two halves of the Threads tab: bookmarks you watch, threads you recently read. */
enum class ThreadsSegment(val labelRes: Int) {
    WATCHED(R.string.threads_watched),
    RECENT(R.string.threads_recent),
}

/**
 * One tab for both lists of threads the user cares about. Each segment keeps its own
 * ViewModel; only the app bar and the segmented control are shared.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadsScreen(
    slots: TabScaffoldSlots,
    onOpenThread: (board: String, threadNo: Long, postNo: Long?) -> Unit,
    onOpenSettings: () -> Unit,
    bookmarksViewModel: BookmarksViewModel = hiltViewModel(),
    historyViewModel: HistoryViewModel = hiltViewModel(),
) {
    var segment by rememberSaveable { mutableStateOf(ThreadsSegment.WATCHED) }
    val bookmarks by bookmarksViewModel.uiState.collectAsStateWithLifecycle()
    val snapshotResult by bookmarksViewModel.snapshotResult.collectAsStateWithLifecycle()
    val history by historyViewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = slots.snackbar
    val scope = rememberCoroutineScope()
    val spacing = LocalSpacing.current
    var searching by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val clearedMessage = stringResource(R.string.history_cleared)

    TabChrome(
        slots = slots,
        topBar = {
            TopAppBar(
                title = {
                    if (segment == ThreadsSegment.RECENT && searching) {
                        HistorySearchField(query = history.query, onQueryChange = historyViewModel::onQueryChange)
                    } else {
                        Column {
                            Text(stringResource(R.string.tab_threads))
                            if (segment == ThreadsSegment.WATCHED) BookmarksCheckingSubtitle(bookmarks.checking)
                        }
                    }
                },
                actions = {
                    when (segment) {
                        ThreadsSegment.WATCHED -> BookmarksMenu(
                            sortOrder = bookmarks.sortOrder,
                            hasDead = bookmarks.hasDead,
                            onSortOrderChanged = bookmarksViewModel::onSortOrderChanged,
                            onRemoveDead = bookmarksViewModel::onRemoveDead,
                        )
                        ThreadsSegment.RECENT -> {
                            IconButton(
                                onClick = {
                                    if (searching) historyViewModel.onQueryChange("")
                                    searching = !searching
                                },
                                enabled = searching || history.totalCount > 0,
                            ) {
                                Icon(
                                    if (searching) Icons.Filled.Close else Icons.Filled.Search,
                                    stringResource(
                                        if (searching) R.string.history_search_close else R.string.history_search,
                                    ),
                                )
                            }
                            IconButton(onClick = { confirmClear = true }, enabled = history.totalCount > 0) {
                                Icon(Icons.Filled.DeleteSweep, stringResource(R.string.action_clear_all))
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, stringResource(R.string.action_open_settings))
                    }
                },
            )
        },
    )
    Column(Modifier.fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(
                Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm),
            ) {
                ThreadsSegment.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = segment == s,
                        onClick = { segment = s },
                        shape = SegmentedButtonDefaults.itemShape(i, ThreadsSegment.entries.size),
                    ) { Text(stringResource(s.labelRes)) }
                }
            }
            Crossfade(
                targetState = segment,
                animationSpec = rememberMotionSpec(LocalMotion.current.medium),
                label = "segment",
                modifier = Modifier.fillMaxSize(),
            ) { shown ->
                when (shown) {
                    ThreadsSegment.WATCHED -> BookmarksList(
                        state = bookmarks,
                        snapshotResult = snapshotResult,
                        onSnapshotResultShown = bookmarksViewModel::onSnapshotResultShown,
                        onScreenVisible = bookmarksViewModel::onScreenVisible,
                        onRefreshAll = bookmarksViewModel::onRefreshAll,
                        onRemove = bookmarksViewModel::onRemove,
                        onUndoRemove = bookmarksViewModel::onUndoRemove,
                        onTogglePinned = bookmarksViewModel::onTogglePinned,
                        onSnapshot = { bookmarksViewModel.snapshot(it.board, it.threadNo) },
                        onOpenThread = { board, no -> onOpenThread(board, no, null) },
                        snackbar = snackbar,
                        modifier = Modifier.fillMaxSize(),
                    )
                    ThreadsSegment.RECENT -> HistoryList(
                        viewModel = historyViewModel,
                        snackbar = snackbar,
                        onOpenThread = onOpenThread,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
    }

    if (confirmClear) {
        HistoryClearDialog(
            onConfirm = {
                confirmClear = false
                historyViewModel.onClearAll()
                scope.launch { snackbar.showSnackbar(clearedMessage) }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

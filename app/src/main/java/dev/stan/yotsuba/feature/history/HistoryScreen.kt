package dev.stan.yotsuba.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.EmptyState
import dev.stan.yotsuba.core.designsystem.component.LoadingSkeleton
import dev.stan.yotsuba.core.designsystem.component.SectionHeader
import dev.stan.yotsuba.core.designsystem.component.SwipeToDeleteRow
import dev.stan.yotsuba.core.designsystem.component.ThreadSummaryRow
import dev.stan.yotsuba.core.designsystem.component.showUndo
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.TimeFormat
import dev.stan.yotsuba.domain.model.HistoryEntry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenThread: (String, Long, Long?) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    val clearedMessage = stringResource(R.string.history_cleared)
    val removedMessage = stringResource(R.string.history_entry_removed)
    val undoLabel = stringResource(R.string.action_undo)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_history)) },
                actions = {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.DeleteSweep, stringResource(R.string.action_clear_all))
                    }
                },
            )
        },
    ) { padding ->
        when {
            !state.loaded -> LoadingSkeleton(Modifier.padding(padding))
            !state.recordingEnabled -> EmptyState(
                title = stringResource(R.string.history_disabled_title),
                explanation = stringResource(R.string.history_disabled_explanation),
                icon = Icons.Filled.History,
                modifier = Modifier.padding(padding),
            )
            state.groups.isEmpty() -> EmptyState(
                title = stringResource(R.string.history_empty_title),
                explanation = stringResource(R.string.history_empty_explanation),
                icon = Icons.Filled.History,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                state.groups.forEach { (bucket, entries) ->
                    item(key = "header_${bucket.name}") { SectionHeader(stringResource(bucket.labelRes)) }
                    items(entries.size, key = { entries[it].board + "/" + entries[it].threadNo }) { i ->
                        val entry = entries[i]
                        SwipeToDeleteRow(onDelete = {
                            viewModel.onRemove(entry)
                            scope.launch {
                                snackbar.showUndo(removedMessage, undoLabel) { viewModel.onUndoRemove(entry) }
                            }
                        }) {
                            HistoryCard(entry) {
                                onOpenThread(entry.board, entry.threadNo, entry.lastScrollPostNo)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.onClearAll()
                    scope.launch { snackbar.showSnackbar(clearedMessage) }
                }) { Text(stringResource(R.string.action_clear_all)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private val HistoryBucket.labelRes: Int
    get() = when (this) {
        HistoryBucket.TODAY -> R.string.history_today
        HistoryBucket.YESTERDAY -> R.string.history_yesterday
        HistoryBucket.THIS_WEEK -> R.string.history_this_week
        HistoryBucket.OLDER -> R.string.history_older
    }

@Composable
private fun HistoryCard(entry: HistoryEntry, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ThreadSummaryRow(
            thumbnailUrl = entry.thumbnailUrl,
            title = entry.displayTitle,
            metadata = "/${entry.board}/ · " + TimeFormat.relative(entry.viewedAt / 1000),
            thumbnailSize = 48.dp,
        )
    }
}

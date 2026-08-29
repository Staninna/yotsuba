package dev.stan.yotsuba.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.animatedListItem
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

/**
 * The Recent segment of the Threads tab. Search and clear live in the host's app bar
 * ([HistorySearchField], [HistoryClearDialog]); this is only the grouped list.
 */
@Composable
fun HistoryList(
    viewModel: HistoryViewModel,
    snackbar: SnackbarHostState,
    onOpenThread: (String, Long, Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val scope = rememberCoroutineScope()
    val removedMessage = stringResource(R.string.history_entry_removed)
    val undoLabel = stringResource(R.string.action_undo)

    when {
        !state.loaded -> LoadingSkeleton(modifier)
        state.groups.isEmpty() && state.query.isNotBlank() -> EmptyState(
            title = stringResource(R.string.history_search_no_matches),
            explanation = stringResource(R.string.history_search_no_matches_explanation, state.query),
            icon = Icons.Filled.Search,
            modifier = modifier,
        )
        state.groups.isEmpty() && state.recordingEnabled -> EmptyState(
            title = stringResource(R.string.history_empty_title),
            explanation = stringResource(R.string.history_empty_explanation),
            icon = Icons.Filled.History,
            modifier = modifier,
        )
        state.groups.isEmpty() -> EmptyState(
            title = stringResource(R.string.history_disabled_title),
            explanation = stringResource(R.string.history_disabled_explanation),
            icon = Icons.Filled.History,
            modifier = modifier,
        )
        else -> LazyColumn(
            contentPadding = PaddingValues(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            modifier = modifier.fillMaxSize(),
        ) {
            if (!state.recordingEnabled) {
                item(key = "paused_banner") { PausedBanner() }
            }
            state.groups.forEach { (bucket, entries) ->
                item(key = "header_${bucket.name}") { SectionHeader(stringResource(bucket.labelRes)) }
                items(entries.size, key = { entries[it].board + "/" + entries[it].threadNo }) { i ->
                    val entry = entries[i]
                    SwipeToDeleteRow(modifier = animatedListItem(), onDelete = {
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

/** The "clear everything?" confirmation; the host decides when to show it. */
@Composable
fun HistoryClearDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_clear_confirm_title)) },
        text = { Text(stringResource(R.string.history_clear_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_clear_all)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val HistoryBucket.labelRes: Int
    get() = when (this) {
        HistoryBucket.TODAY -> R.string.history_today
        HistoryBucket.YESTERDAY -> R.string.history_yesterday
        HistoryBucket.THIS_WEEK -> R.string.history_this_week
        HistoryBucket.OLDER -> R.string.history_older
    }

@Composable
fun HistorySearchField(query: String, onQueryChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.history_search_hint)) },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
}

/** Rows stay readable while recording is off; this just says why nothing new appears. */
@Composable
private fun PausedBanner() {
    val spacing = LocalSpacing.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(spacing.md),
        ) {
            Icon(Icons.Filled.PauseCircle, contentDescription = null)
            Column(Modifier.padding(start = spacing.md)) {
                Text(
                    stringResource(R.string.history_paused_banner),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.history_paused_banner_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntry, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ThreadSummaryRow(
            thumbnailUrl = entry.thumbnailUrl,
            title = entry.displayTitle,
            metadata = "/${entry.board}/ · " + TimeFormat.relativeMillis(entry.viewedAt),
            thumbnailSize = 48.dp,
        )
    }
}

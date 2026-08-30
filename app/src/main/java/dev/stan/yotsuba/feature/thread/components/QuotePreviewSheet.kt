package dev.stan.yotsuba.feature.thread.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.errorMessage
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.TimeFormat
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.feature.thread.Ghost
import dev.stan.yotsuba.feature.thread.GhostSource
import dev.stan.yotsuba.feature.thread.PreviewSheet

/**
 * The quote preview as a small thread focused on one post: what it quotes above (folded
 * into a strip until opened), the post itself, and what quotes it below. Tapping any of
 * the other posts refocuses the sheet on it; the header's back arrow returns. Each focused
 * post keeps its own scroll position while it is on the path, so going back lands where
 * the reader left.
 *
 * A post from another thread (a "ghost") says where it came from under its number and
 * offers "Open thread" instead of "Go to"; while its thread loads the sheet shows a
 * spinner, and a post no copy holds says so.
 *
 * Back presses are the caller's: the sheet does not dismiss itself on back, so system back
 * can pop one post at a time.
 *
 * @param onOpenThread "Open thread" on a ghost: (board, threadNo, postNo) to navigate to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotePreviewSheet(
    preview: PreviewSheet,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onGoTo: (Long) -> Unit,
    onOpenThread: (String, Long, Long) -> Unit,
    onFocus: (Long) -> Unit,
    postCard: @Composable (ThreadPost) -> Unit,
) {
    val spacing = LocalSpacing.current
    val focusNo = preview.focusNo
    // Each focused post's list state lives under its own saveable key, so it survives
    // rotation and is still there when the back arrow returns to that post. A ghost is
    // keyed with its thread: the same number can exist in two boards.
    val focusStates = rememberSaveableStateHolder()
    val focusKey = preview.ghost?.let { "${it.board}/${it.threadNo}/$focusNo" } ?: focusNo.toString()
    var parentsShown by rememberSaveable(focusKey) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        focusStates.SaveableStateProvider(focusKey) {
        val listState = rememberLazyListState()
        Column(Modifier.fillMaxHeight(0.7f)) {
            Header(
                preview,
                onBack = onBack,
                onGoTo = {
                    val ghost = preview.ghost
                    if (ghost == null) onGoTo(focusNo) else onOpenThread(ghost.board, ghost.threadNo, focusNo)
                },
                onDismiss = onDismiss,
            )
            HorizontalDivider()
            if (preview !is PreviewSheet.Post) {
                GhostShell(preview)
                return@Column
            }
            val focus = preview.focus
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (preview.parents.isNotEmpty()) {
                    item(key = "parents") {
                        SectionRow(
                            label = pluralStringResource(
                                R.plurals.thread_preview_replying_to, preview.parents.size, preview.parents.size,
                            ),
                            expanded = parentsShown,
                            onToggle = { parentsShown = !parentsShown },
                        )
                    }
                    if (parentsShown) {
                        items(preview.parents, key = { "parent-${it.no}" }) { parent ->
                            Box(Modifier.clickable { onFocus(parent.no) }) { postCard(parent) }
                        }
                    }
                }
                item(key = "focus-${focus.no}") { postCard(focus) }
                if (preview.replies.isNotEmpty()) {
                    item(key = "replies") {
                        Text(
                            pluralStringResource(R.plurals.thread_preview_replies, preview.replies.size, preview.replies.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = spacing.xs),
                        )
                    }
                    items(preview.replies, key = { "reply-${it.no}" }) { reply ->
                        Box(Modifier.clickable { onFocus(reply.no) }) { postCard(reply) }
                    }
                }
            }
        }
        }
    }
}

/** The sheet's body while a ghost's thread is fetched, or when no copy of it has the post. */
@Composable
private fun GhostShell(preview: PreviewSheet) {
    val spacing = LocalSpacing.current
    Box(Modifier.fillMaxWidth().padding(spacing.lg), contentAlignment = Alignment.Center) {
        when (preview) {
            is PreviewSheet.Loading -> CircularProgressIndicator(Modifier.size(spacing.lg))
            is PreviewSheet.Missing -> Text(
                if (preview.error == NetworkError.NotFound) stringResource(R.string.thread_ghost_not_found)
                else errorMessage(preview.error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is PreviewSheet.Post -> {}
        }
    }
}

/**
 * Back arrow, the focused post's number, name and time (or, for a ghost, where it came
 * from and which copy), "Go to" or "Open thread", close; then the path.
 */
@Composable
private fun Header(preview: PreviewSheet, onBack: () -> Unit, onGoTo: () -> Unit, onDismiss: () -> Unit) {
    val spacing = LocalSpacing.current
    val focus = (preview as? PreviewSheet.Post)?.focus
    val ghost = preview.ghost
    Column(Modifier.padding(horizontal = spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (preview.canGoBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.thread_preview_back))
                }
            } else {
                Spacer(Modifier.width(spacing.sm))
            }
            Column(Modifier.weight(1f)) {
                Text(">>${preview.focusNo}", style = MaterialTheme.typography.titleSmall)
                if (focus != null) {
                    Text(
                        "${focus.name} · ${TimeFormat.relative(focus.timeSeconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (ghost != null) {
                    Text(
                        ghostLine(ghost),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onGoTo) {
                Text(stringResource(if (ghost == null) R.string.thread_go_to_post else R.string.thread_ghost_open_thread))
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, stringResource(R.string.thread_preview_close))
            }
        }
        if (preview.canGoBack) {
            Text(
                preview.path.joinToString(" › ") { ">>$it" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.padding(start = spacing.sm, bottom = spacing.xs),
            )
        }
    }
}

/** "From /b/123 · Saved copy": where the ghost lives, and the copy it was read from if known. */
@Composable
private fun ghostLine(ghost: Ghost): String {
    val from = stringResource(R.string.thread_ghost_from, ghost.board, ghost.threadNo)
    val source = when (val s = ghost.source) {
        null -> return from
        GhostSource.Live -> stringResource(R.string.thread_ghost_source_live)
        GhostSource.Saved -> stringResource(R.string.thread_ghost_source_saved)
        is GhostSource.Archived -> stringResource(R.string.thread_ghost_source_archived, s.archive.name.lowercase())
    }
    return "$from · $source"
}

/** The fold line above the parents: the count, and a chevron for its state. */
@Composable
private fun SectionRow(label: String, expanded: Boolean, onToggle: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = spacing.xs),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            stringResource(if (expanded) R.string.thread_preview_hide_parents else R.string.thread_preview_show_parents),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

package dev.stan.yotsuba.feature.thread.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.TimeFormat
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.feature.thread.PreviewSheet

/**
 * The quote preview as a small thread focused on one post: what it quotes above (folded
 * into a strip until opened), the post itself, and what quotes it below. Tapping any of
 * the other posts refocuses the sheet on it; the header's back arrow returns. Each focused
 * post keeps its own scroll position while it is on the path, so going back lands where
 * the reader left.
 *
 * Back presses are the caller's: the sheet does not dismiss itself on back, so system back
 * can pop one post at a time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotePreviewSheet(
    preview: PreviewSheet,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onGoTo: (Long) -> Unit,
    onFocus: (Long) -> Unit,
    postCard: @Composable (ThreadPost) -> Unit,
) {
    val spacing = LocalSpacing.current
    val focus = preview.focus
    // Each focused post's list state lives under its own saveable key, so it survives
    // rotation and is still there when the back arrow returns to that post.
    val focusStates = rememberSaveableStateHolder()
    var parentsShown by rememberSaveable(focus.no) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        focusStates.SaveableStateProvider(focus.no) {
        val listState = rememberLazyListState()
        Column(Modifier.fillMaxHeight(0.7f)) {
            Header(preview, onBack = onBack, onGoTo = { onGoTo(focus.no) }, onDismiss = onDismiss)
            HorizontalDivider()
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

/** Back arrow, the focused post's number, name and time, "Go to", close; then the path. */
@Composable
private fun Header(preview: PreviewSheet, onBack: () -> Unit, onGoTo: () -> Unit, onDismiss: () -> Unit) {
    val spacing = LocalSpacing.current
    val focus = preview.focus
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
                Text(">>${focus.no}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${focus.name} · ${TimeFormat.relative(focus.timeSeconds)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onGoTo) { Text(stringResource(R.string.thread_go_to_post)) }
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

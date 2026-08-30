package dev.stan.yotsuba.feature.thread.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.feature.media.shareText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ThreadTopBar(
    board: String,
    threadNo: Long,
    title: String,
    bookmarked: Boolean,
    autoRefreshEnabled: Boolean,
    /** Replies to the user's claimed posts; hidden when zero. */
    repliesToMe: Int,
    /** Tap and hold on the replies indicator; the screen routes them like a quotelink. */
    onRepliesToMeTap: (() -> Unit)? = null,
    onRepliesToMeLongPress: (() -> Unit)? = null,
    /** Posts the content filters hid or stubbed; hidden when zero. */
    filteredCount: Int = 0,
    /** Active poster-ID filter; the chip clears it. */
    filterPosterId: String?,
    onClearFilter: () -> Unit,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenGallery: () -> Unit,
    treeView: Boolean,
    onToggleTreeView: () -> Unit,
    onToggleAutoRefresh: () -> Unit,
    onOpenExternal: (String) -> Unit,
    /** When set, "Open in browser" goes here instead of 4chan; share and copy keep the 4chan link. */
    archiveUrl: String? = null,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val spacing = LocalSpacing.current
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Independent signals, shown side by side: none of them may hide another.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (filterPosterId != null) {
                        InputChip(
                            selected = true,
                            onClick = onClearFilter,
                            label = { Text(stringResource(R.string.thread_filter_id, filterPosterId)) },
                            trailingIcon = { Icon(Icons.Filled.Close, stringResource(R.string.thread_filter_clear)) },
                        )
                    }
                    if (repliesToMe > 0) {
                        RepliesToMeChip(repliesToMe, onRepliesToMeTap, onRepliesToMeLongPress)
                    }
                    if (filteredCount > 0) {
                        Text(
                            pluralStringResource(R.plurals.thread_filtered_count, filteredCount, filteredCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
        },
        actions = {
            IconButton(onClick = onToggleBookmark) {
                Icon(
                    if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    stringResource(
                        if (bookmarked) R.string.thread_remove_bookmark else R.string.thread_bookmark
                    ),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val webUrl = Urls.threadWebUrl(board, threadNo)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.thread_share)) },
                    onClick = {
                        menuOpen = false
                        shareText(context, webUrl)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.thread_copy_link)) },
                    onClick = {
                        menuOpen = false
                        clipboard.setText(AnnotatedString(webUrl))
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.thread_search_in_thread)) },
                    onClick = { menuOpen = false; onOpenSearch() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.thread_gallery)) },
                    onClick = { menuOpen = false; onOpenGallery() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.thread_open_in_browser)) },
                    onClick = { menuOpen = false; onOpenExternal(archiveUrl ?: webUrl) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.thread_tree_view)) },
                    trailingIcon = { if (treeView) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = { menuOpen = false; onToggleTreeView() },
                    modifier = Modifier.semantics { toggleableState = ToggleableState(treeView) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.thread_auto_refresh)) },
                    trailingIcon = {
                        if (autoRefreshEnabled) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    onClick = { menuOpen = false; onToggleAutoRefresh() },
                    modifier = Modifier.semantics {
                        toggleableState = ToggleableState(autoRefreshEnabled)
                    },
                )
            }
        },
    )
}

/**
 * "N replies to you" as a chip, so it has a role, a ripple and a full-size touch target.
 * The hold lives on the label, inside the chip's own click handling: a held label
 * consumes the gesture, so the chip's tap does not also fire.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RepliesToMeChip(count: Int, onTap: (() -> Unit)?, onLongPress: (() -> Unit)?) {
    AssistChip(
        onClick = onTap ?: {},
        enabled = onTap != null,
        label = {
            Text(
                pluralStringResource(R.plurals.thread_replies_to_you, count, count),
                modifier = if (onTap == null || onLongPress == null) Modifier else Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTap,
                    onLongClick = onLongPress,
                ),
            )
        },
    )
}

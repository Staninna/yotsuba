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

/**
 * What the top bar shows, derived once from the loaded content. The defaults are what the
 * bar shows before the thread has loaded, so the caller can build one from a null content.
 */
data class ThreadTopBarState(
    val title: String,
    val bookmarked: Boolean = false,
    val autoRefreshEnabled: Boolean = false,
    /** Replies to the user's claimed posts; hidden when zero. */
    val repliesToMe: Int = 0,
    /** The newest of them; the replies indicator routes to it like a quotelink. */
    val latestReplyToMe: Long? = null,
    /** Posts the content filters hid or stubbed; hidden when zero. */
    val filteredCount: Int = 0,
    /** Active poster-ID filter; the chip clears it. */
    val filterPosterId: String? = null,
    /** Attachments in the thread; the save-all entry is greyed out at zero. */
    val mediaCount: Int = 0,
    val treeView: Boolean = false,
    /** When set, "Open in browser" goes here instead of 4chan; share and copy keep the 4chan link. */
    val archiveUrl: String? = null,
)

/**
 * Everything the top bar can ask of the screen. One instance, remembered, so the bar's
 * inputs stay equal across recompositions the way [PostCardActions] keeps the cards'.
 */
data class ThreadTopBarActions(
    val onBack: () -> Unit,
    val onToggleBookmark: () -> Unit,
    val onRefresh: () -> Unit,
    val onOpenSearch: () -> Unit,
    val onOpenGallery: () -> Unit,
    val onSaveAll: () -> Unit,
    val onToggleTreeView: () -> Unit,
    val onToggleAutoRefresh: () -> Unit,
    val onOpenExternal: (String) -> Unit,
    val onClearFilter: () -> Unit,
    /** Tap and hold on the replies indicator, given the newest reply's number. */
    val onRepliesToMeTap: (Long) -> Unit,
    val onRepliesToMeLongPress: (Long) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ThreadTopBar(
    board: String,
    threadNo: Long,
    state: ThreadTopBarState,
    actions: ThreadTopBarActions,
) {
    val onRepliesToMeTap = state.latestReplyToMe?.let { no -> { actions.onRepliesToMeTap(no) } }
    val onRepliesToMeLongPress = state.latestReplyToMe?.let { no -> { actions.onRepliesToMeLongPress(no) } }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val spacing = LocalSpacing.current
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Independent signals, shown side by side: none of them may hide another.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.filterPosterId != null) {
                        InputChip(
                            selected = true,
                            onClick = actions.onClearFilter,
                            label = { Text(stringResource(R.string.thread_filter_id, state.filterPosterId)) },
                            trailingIcon = { Icon(Icons.Filled.Close, stringResource(R.string.thread_filter_clear)) },
                        )
                    }
                    if (state.repliesToMe > 0) {
                        RepliesToMeChip(state.repliesToMe, onRepliesToMeTap, onRepliesToMeLongPress)
                    }
                    if (state.filteredCount > 0) {
                        Text(
                            pluralStringResource(R.plurals.thread_filtered_count, state.filteredCount, state.filteredCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = actions.onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
        },
        actions = {
            IconButton(onClick = actions.onToggleBookmark) {
                Icon(
                    if (state.bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    stringResource(
                        if (state.bookmarked) R.string.thread_remove_bookmark else R.string.thread_bookmark
                    ),
                )
            }
            IconButton(onClick = actions.onRefresh) {
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
                    onClick = { menuOpen = false; actions.onOpenSearch() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.thread_gallery)) },
                    onClick = { menuOpen = false; actions.onOpenGallery() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.thread_save_all_media, state.mediaCount)) },
                    enabled = state.mediaCount > 0,
                    onClick = { menuOpen = false; actions.onSaveAll() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.thread_open_in_browser)) },
                    onClick = { menuOpen = false; actions.onOpenExternal(state.archiveUrl ?: webUrl) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.thread_tree_view)) },
                    trailingIcon = { if (state.treeView) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = { menuOpen = false; actions.onToggleTreeView() },
                    modifier = Modifier.semantics { toggleableState = ToggleableState(state.treeView) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.thread_auto_refresh)) },
                    trailingIcon = {
                        if (state.autoRefreshEnabled) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    onClick = { menuOpen = false; actions.onToggleAutoRefresh() },
                    modifier = Modifier.semantics {
                        toggleableState = ToggleableState(state.autoRefreshEnabled)
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

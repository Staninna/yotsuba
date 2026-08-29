package dev.stan.yotsuba.feature.thread.components

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.util.Urls

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadTopBar(
    board: String,
    threadNo: Long,
    title: String,
    bookmarked: Boolean,
    autoRefreshEnabled: Boolean,
    /** Replies to the user's claimed posts; hidden when zero. */
    repliesToMe: Int,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleAutoRefresh: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (repliesToMe > 0) {
                    Text(
                        pluralStringResource(R.plurals.thread_replies_to_you, repliesToMe, repliesToMe),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
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
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, webUrl)
                        }
                        context.startActivity(Intent.createChooser(send, null))
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
                    text = { Text(stringResource(R.string.thread_open_in_browser)) },
                    onClick = { menuOpen = false; onOpenExternal(webUrl) },
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

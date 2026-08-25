package dev.stan.yotsuba.feature.vault

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.util.FileSize
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.feature.media.MediaFeedViewer
import dev.stan.yotsuba.feature.media.ViewerPage
import dev.stan.yotsuba.feature.media.rememberMediaFeedState
import dev.stan.yotsuba.feature.media.rememberPipController
import dev.stan.yotsuba.feature.media.shareMediaFile
import java.io.File
import kotlinx.coroutines.launch

/** In-app explorer over the on-disk vault: boards → threads → media grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(viewModel: VaultViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<VaultEntry?>(null) }

    BackHandler(enabled = state.selection.board != null) { viewModel.navigateUp() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(vaultTitle(state), maxLines = 1) },
                    navigationIcon = {
                        if (state.selection.board != null) {
                            IconButton(onClick = { viewModel.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.media_back))
                            }
                        }
                    },
                    actions = {
                        if (state.rescanning) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { viewModel.rescan() }) {
                                Icon(Icons.Filled.Refresh, stringResource(R.string.vault_rescan))
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                if (state.scopeEntries.isNotEmpty() && viewModel.hasStorageAccess()) {
                    VaultShuffleFab(state.scopeEntries) { viewModel.startShuffle(it) }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                VaultExplorer(
                    state = state,
                    hasStorageAccess = viewModel.hasStorageAccess(),
                    onOpenBoard = viewModel::openBoard,
                    onOpenThread = viewModel::openThread,
                    onOpenEntry = { viewModel.openViewer(it.url) },
                    onLongPressEntry = { deleting = it },
                )
            }
        }

        state.viewer?.let { viewer ->
            VaultViewer(
                viewer = viewer,
                autoAdvance = viewModel.autoAdvance,
                onToggleAutoAdvance = { viewModel.autoAdvance = !viewModel.autoAdvance },
                onPageViewed = { viewModel.onViewerPage(it.url) },
                onDismiss = { viewModel.closeViewer() },
            )
        }
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.vault_delete_title)) },
            text = { Text(stringResource(R.string.vault_delete_body, entry.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { viewModel.delete(entry.url) }
                }) { Text(stringResource(R.string.vault_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.vault_cancel)) }
            },
        )
    }
}

/**
 * Full-screen in-app viewer over a saved-thread group: vertical TikTok-style swipe feed,
 * zoomable images and looping videos playing straight from disk.
 */
@Composable
private fun VaultViewer(
    viewer: VaultViewerState,
    autoAdvance: Boolean,
    onToggleAutoAdvance: () -> Unit,
    onPageViewed: (VaultEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val entries = viewer.entries

    BackHandler { onDismiss() }

    val feed = rememberMediaFeedState(initialIndex = viewer.index) { entries.size }
    val pip = rememberPipController(
        onPrev = { feed.previous() },
        onNext = { feed.next(entries.lastIndex) },
        onTogglePlayPause = { feed.playbackOn = !feed.playbackOn },
    )

    MediaFeedViewer(
        pages = entries.map { it.toViewerPage() },
        feed = feed,
        pip = pip,
        autoAdvance = autoAdvance,
        onToggleAutoAdvance = onToggleAutoAdvance,
        onPageViewed = { page -> entries.getOrNull(page)?.let(onPageViewed) },
        onDismiss = onDismiss,
        topBarActions = {
            IconButton(onClick = {
                entries.getOrNull(feed.currentPage)?.let { entry ->
                    shareMediaFile(context, File(entry.absolutePath), entry.ext.orEmpty())
                }
            }) {
                Icon(Icons.Filled.Share, stringResource(R.string.thread_share), tint = Color.White)
            }
        },
    )
}

private fun VaultEntry.toViewerPage(): ViewerPage = ViewerPage(
    isVideo = isVideo,
    videoUri = Uri.fromFile(File(absolutePath)).toString(),
    imageModel = File(absolutePath),
    thumbnailModel = if (isVideo) thumbnailUrl else null,
    width = width ?: 0,
    height = height ?: 0,
    title = displayName,
    subtitle = buildString {
        sizeBytes?.let { append(" · ${FileSize.format(it)}") }
        if ((width ?: 0) > 0) append(" · ${width}×${height}")
        (location as? VaultLocation.Thread)?.subject?.let { append(" · $it") }
    },
    contentDescription = displayName,
)

@Composable
private fun vaultTitle(state: VaultUiState): String = when {
    state.selection.board == null -> stringResource(R.string.vault_title)
    state.selection.thread == null -> boardTitle(state.selection.board!!)
    else -> {
        val location = state.selection.thread
        (location as? VaultLocation.Thread)?.subject
            ?: stringResource(
                R.string.vault_thread_untitled,
                (location as? VaultLocation.Thread)?.threadNo ?: 0L,
            )
    }
}

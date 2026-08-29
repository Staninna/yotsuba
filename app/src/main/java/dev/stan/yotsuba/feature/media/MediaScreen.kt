package dev.stan.yotsuba.feature.media

import android.net.Uri
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.util.FileSize
import dev.stan.yotsuba.data.repository.DownloadState
import dev.stan.yotsuba.domain.model.MediaItem
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun MediaScreen(
    board: String,
    threadNo: Long,
    initialPostNo: Long,
    onClose: () -> Unit,
    viewModel: MediaViewModel = hiltViewModel<MediaViewModel, MediaViewModel.Factory>(
        creationCallback = { it.create(board, threadNo, initialPostNo) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val grantAccessMessage = stringResource(R.string.media_grant_storage)
    val shareFailedMessage = stringResource(R.string.media_share_failed)

    if (!state.loaded) return

    var autoAdvance by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current
    // Queued + running saves. Failed ones are not "in progress"; they wait on the icon.
    val pending = state.downloadStates.count { (_, s) -> s !is DownloadState.Failed }

    ThreadMediaViewer(
        pages = state.items.map { it.toViewerPage(context, state, pending) },
        thread = state.thread,
        behaviour = state.behaviour,
        initialIndex = state.initialIndex,
        muted = !state.defaultUnmuted,
        playing = state.autoplay,
        autoAdvance = autoAdvance,
        onToggleAutoAdvance = { autoAdvance = !autoAdvance },
        postNoAt = { page -> state.items.getOrNull(page)?.postNo },
        indexOfPost = { postNo -> state.items.indexOfFirst { it.postNo == postNo } },
        onPageViewed = { page ->
            state.items.getOrNull(page)?.let { viewModel.onMediaViewed(it.postNo) }
        },
        onDismiss = onClose,
        activeDownloads = pending,
        onLongPressPage = { page ->
            val item = state.items.getOrNull(page)
            if (state.behaviour.holdToSave && item != null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                saveToVault(
                    context = context,
                    hasAccess = viewModel.hasStorageAccess(),
                    onAccessNeeded = { scope.launch { snackbar.showSnackbar(grantAccessMessage) } },
                    save = { viewModel.enqueueSave(item) },
                )
            }
        },
        overlay = {
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        },
    ) { page, _ ->
        val item = state.items.getOrNull(page)
        DownloadAction(
            status = saveStatusOf(
                downloaded = item != null && item.fullUrl in state.downloadedUrls,
                queueState = item?.let { state.downloadStates[it.fullUrl] },
            ),
            interceptClick = {
                when {
                    item == null -> true
                    !viewModel.hasStorageAccess() -> {
                        requestAllFilesAccess(context)
                        scope.launch { snackbar.showSnackbar(grantAccessMessage) }
                        true
                    }
                    else -> false
                }
            },
            onSave = { item?.let { viewModel.enqueueSave(it) } },
            onRemove = { item?.let { viewModel.removeDownload(it.fullUrl) } },
            onRedownload = { item?.let { viewModel.redownload(it) } },
            onCancel = { item?.let { viewModel.cancelQueued(it.fullUrl) } },
            onDismissFailed = { item?.let { viewModel.dismissFailed(it.fullUrl) } },
        )
        IconButton(
            enabled = !sharing,
            onClick = {
                item?.let { m ->
                    scope.launch {
                        sharing = true
                        val file = viewModel.prepareShare(m)
                        sharing = false
                        if (file == null) {
                            snackbar.showSnackbar(shareFailedMessage)
                        } else {
                            shareMediaFile(context, file, m.ext)
                        }
                    }
                }
            },
        ) {
            if (sharing) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Icon(Icons.Filled.Share, stringResource(R.string.thread_share), tint = Color.White)
            }
        }
    }
}

@Composable
private fun MediaItem.toViewerPage(
    context: android.content.Context,
    state: MediaUiState,
    /** Saves in flight app-wide; surfaces in the chrome subtitle as "↓n". */
    pending: Int,
): ViewerPage {
    // Already-saved media plays straight from the vault file — no buffering.
    val localPath = state.savedPaths[fullUrl]
    return ViewerPage(
        isVideo = isVideo,
        videoUri = localPath?.let { Uri.fromFile(File(it)).toString() } ?: fullUrl,
        imageModel = localPath?.let { File(it) } ?: ImageRequest.Builder(context)
            .data(fullUrl)
            .crossfade(false)
            .build(),
        thumbnailModel = thumbnailUrl,
        width = width,
        height = height,
        title = displayName,
        subtitle = buildString {
            append(" · ${FileSize.format(sizeBytes)} · ${width}×${height}")
            if (pending > 0) append(" · ↓$pending")
        },
        contentDescription = stringResource(
            R.string.media_image_description, displayName, width, height,
        ),
    )
}

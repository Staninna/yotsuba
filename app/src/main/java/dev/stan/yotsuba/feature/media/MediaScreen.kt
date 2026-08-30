package dev.stan.yotsuba.feature.media

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.OnResumeEffect
import dev.stan.yotsuba.core.designsystem.component.errorMessage
import dev.stan.yotsuba.core.designsystem.rememberHaptics
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
    // The grant happens in a system settings page; re-check it every time we come back.
    OnResumeEffect(viewModel::refreshStorageAccess)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val grantAccessMessage = stringResource(R.string.media_grant_storage)
    val shareFailedMessage = stringResource(R.string.media_share_failed)

    when (val phase = state.phase) {
        ViewerPhase.Loading -> {
            ViewerPlaceholder(onClose = onClose) {
                CircularProgressIndicator(color = Color.White)
            }
            return
        }
        ViewerPhase.Empty -> {
            ViewerPlaceholder(onClose = onClose) {
                Text(
                    stringResource(R.string.media_viewer_empty),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
            return
        }
        is ViewerPhase.Error -> {
            ViewerPlaceholder(onClose = onClose) {
                Text(
                    errorMessage(phase.error),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = viewModel::retry) {
                    Text(stringResource(R.string.action_retry), color = Color.White)
                }
            }
            return
        }
        ViewerPhase.Ready -> Unit
    }

    var autoAdvance by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    val haptics = rememberHaptics()
    // Queued + running saves. Failed ones are not "in progress"; they wait on the icon.
    val pending = state.saveStatuses.count { (_, s) -> s.inProgress }

    // Rebuilt only when something a page reads changes, not on every save-status tick.
    val pages = remember(state.items, state.saved, state.deferHeavyMedia, context) {
        state.items.map { it.toViewerPage(context, state) }
    }

    ThreadMediaViewer(
        pages = pages,
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
                haptics.longPress()
                saveToVault(
                    context = context,
                    hasAccess = state.hasStorageAccess,
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
            status = item?.let { state.saveStatuses[it.fullUrl] },
            interceptClick = {
                when {
                    item == null -> true
                    !state.hasStorageAccess -> {
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
            onRetry = { item?.let { viewModel.retryFailed(it.fullUrl) } },
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

/** The black full-screen ground with a close button, for everything that is not media. */
@Composable
private fun ViewerPlaceholder(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
        ) {
            Icon(Icons.Filled.Close, stringResource(R.string.media_close), tint = Color.White)
        }
    }
}

private fun MediaItem.toViewerPage(context: Context, state: MediaUiState): ViewerPage {
    // Already-saved media plays straight from the vault file — no buffering.
    val localPath = state.savedPath(fullUrl)
    val description = context.getString(R.string.media_image_description, displayName, width, height)
    return if (isVideo) {
        ViewerPage.Video(
            uri = localPath?.let { Uri.fromFile(File(it)).toString() } ?: fullUrl,
            thumbnailModel = thumbnailUrl,
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            title = displayName,
            contentDescription = description,
            soundUrl = soundUrl,
            sharedKey = fullUrl,
        )
    } else {
        ViewerPage.Image(
            model = localPath?.let { File(it) } ?: ImageRequest.Builder(context)
                .data(fullUrl)
                .crossfade(false)
                .build(),
            thumbnailModel = thumbnailUrl,
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            title = displayName,
            contentDescription = description,
            // A vault copy costs nothing to show; only the network fetch is deferred.
            deferLoad = state.deferHeavyMedia && localPath == null,
            soundUrl = soundUrl,
            sharedKey = fullUrl,
        )
    }
}

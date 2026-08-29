package dev.stan.yotsuba.feature.media

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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

/** Swipe distance that commits a horizontal navigation. */
private const val SWIPE_COMMIT_PX = 160f

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

    val feed = rememberMediaFeedState(
        initialIndex = state.initialIndex,
        initialMuted = !state.defaultUnmuted,
        initialPlaying = state.autoplay,
    ) { state.items.size }
    LaunchedEffect(state.defaultUnmuted) { feed.muted = !state.defaultUnmuted }
    LaunchedEffect(state.autoplay) { feed.playbackOn = state.autoplay }

    val stack = rememberViewerStack(state.initialIndex, feed::scrollTo)
    val pip = rememberPipController(
        onPrev = { feed.previous() },
        onNext = { feed.next(state.items.lastIndex) },
        onTogglePlayPause = { feed.playbackOn = !feed.playbackOn },
    )

    BackHandler(enabled = stack.size > 1) { stack.pop() }

    LaunchedEffect(pip.inPipMode) {
        if (pip.inPipMode && !stack.onMedia) stack.collapseToMedia(feed.currentPage)
    }

    fun jumpToMedia(postNo: Long) {
        val index = state.items.indexOfFirst { it.postNo == postNo }
        if (index >= 0) stack.push(ViewerEntry.Media(index))
    }

    val pages = state.items.map { it.toViewerPage(context, state) }

    MediaFeedViewer(
        pages = pages,
        feed = feed,
        pip = pip,
        autoAdvance = autoAdvance,
        onToggleAutoAdvance = { autoAdvance = !autoAdvance },
        onPageViewed = { page ->
            stack.syncToPage(page)
            state.items.getOrNull(page)?.let { viewModel.onMediaViewed(it.postNo) }
        },
        onDismiss = onClose,
        feedActive = stack.onMedia,
        behaviour = state.behaviour,
        // Horizontal navigation: left = open replies of the current post, right = back.
        modifier = Modifier.pointerInput(pip.inPipMode) {
            if (pip.inPipMode) return@pointerInput
            var dragTotal = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragTotal = 0f },
                onHorizontalDrag = { _, delta -> dragTotal += delta },
                onDragEnd = {
                    when {
                        dragTotal < -SWIPE_COMMIT_PX -> {
                            val t = stack.top
                            if (t is ViewerEntry.Media) {
                                state.items.getOrNull(t.index)
                                    ?.let { stack.push(ViewerEntry.Panel(it.postNo)) }
                            }
                        }
                        // At the bottom of the stack, back-swipe leaves the viewer.
                        dragTotal > SWIPE_COMMIT_PX ->
                            if (stack.size > 1) stack.pop() else onClose()
                    }
                },
            )
        },
        topBarActions = {
            val item = state.items.getOrNull(feed.currentPage)
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Filled.Share, stringResource(R.string.thread_share), tint = Color.White)
                }
            }
        },
        overlay = {
            // Reply panel layer, animated push/pop; the media feed stays alive underneath.
            AnimatedContent(
                targetState = stack.top as? ViewerEntry.Panel,
                transitionSpec = {
                    if (stack.navForward) {
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                    } else {
                        slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label = "panel",
            ) { panel ->
                if (panel != null) {
                    SubThreadPanel(
                        rootPostNo = panel.rootPostNo,
                        depth = stack.panelDepth,
                        state = state,
                        onOpenSubThread = { stack.push(ViewerEntry.Panel(it)) },
                        onJumpToMedia = ::jumpToMedia,
                        onBack = stack::pop,
                    )
                } else {
                    Box(Modifier.fillMaxSize())
                }
            }
            if (!pip.inPipMode) {
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            }
        },
    )
}

@Composable
private fun MediaItem.toViewerPage(
    context: android.content.Context,
    state: MediaUiState,
): ViewerPage {
    // Already-saved media plays straight from the vault file — no buffering.
    val localPath = state.savedPaths[fullUrl]
    // Pending saves (queued + downloading) surface in the chrome subtitle as "↓n".
    val pending = state.downloadStates.count { (_, s) -> s !is DownloadState.Failed }
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

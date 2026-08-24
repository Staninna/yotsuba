package dev.stan.yotsuba.feature.media

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon as AndroidIcon
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.FileSize
import dev.stan.yotsuba.data.repository.DownloadState
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.feature.thread.components.BodyTap
import dev.stan.yotsuba.feature.thread.components.PostCard
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState

private const val PIP_ACTION = "dev.stan.yotsuba.PIP_ACTION"
private const val PIP_EXTRA = "what"
private const val PIP_PREV = 0
private const val PIP_PLAY_PAUSE = 1
private const val PIP_NEXT = 2

/** Swipe distance that commits a horizontal navigation. */
private const val SWIPE_COMMIT_PX = 160f

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The viewer is a stack: media pages and reply panels push on top of each other.
 * Swipe left anywhere to open the current post's replies; swipe right to go back
 * one step, whatever that step was (media page or panel).
 */
private sealed interface ViewerEntry {
    data class Media(val index: Int) : ViewerEntry
    data class Panel(val rootPostNo: Long) : ViewerEntry
}

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
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var chromeVisible by remember { mutableStateOf(true) }
    val grantAccessMessage = stringResource(R.string.media_grant_storage)

    if (!state.loaded) return

    var muted by remember(state.defaultUnmuted) { mutableStateOf(!state.defaultUnmuted) }
    var playbackOn by remember(state.autoplay) { mutableStateOf(state.autoplay) }
    var autoAdvance by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val shareFailedMessage = stringResource(R.string.media_share_failed)

    LaunchedEffect(chromeVisible) {
        if (chromeVisible) {
            delay(3_000)
            chromeVisible = false
        }
    }

    val vPager = rememberPagerState(initialPage = state.initialIndex) { state.items.size }

    var stack by remember { mutableStateOf<List<ViewerEntry>>(listOf(ViewerEntry.Media(state.initialIndex))) }
    // Slide direction for panel transitions: true = pushing deeper, false = popping back.
    var navForward by remember { mutableStateOf(true) }
    val top = stack.last()
    val onMedia = top is ViewerEntry.Media

    fun push(entry: ViewerEntry) {
        navForward = true
        stack = stack + entry
        if (entry is ViewerEntry.Media) scope.launch { vPager.scrollToPage(entry.index) }
    }

    fun pop() {
        if (stack.size <= 1) return
        navForward = false
        stack = stack.dropLast(1)
        // Restore the media page that lives directly under the new top.
        val mediaBelow = stack.lastOrNull { it is ViewerEntry.Media } as? ViewerEntry.Media
        if (mediaBelow != null) scope.launch { vPager.scrollToPage(mediaBelow.index) }
    }

    // Vertical swipes on the feed rewrite the top media entry so back lands where you left.
    LaunchedEffect(vPager.currentPage) {
        val t = stack.last()
        if (t is ViewerEntry.Media && t.index != vPager.currentPage) {
            stack = stack.dropLast(1) + ViewerEntry.Media(vPager.currentPage)
        }
        state.items.getOrNull(vPager.currentPage)?.let { viewModel.onMediaViewed(it.postNo) }
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    // Picture-in-picture.
    var pipMode by remember { mutableStateOf(false) }
    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose {}
        val listener = Consumer<androidx.core.app.PictureInPictureModeChangedInfo> {
            pipMode = it.isInPictureInPictureMode
        }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    LaunchedEffect(pipMode) {
        if (pipMode && !onMedia) {
            navForward = false
            stack = listOf(ViewerEntry.Media(vPager.currentPage))
        }
    }

    fun pipParams(item: MediaItem?, playing: Boolean): PictureInPictureParams {
        val rawAspect = if (item != null && item.width > 0 && item.height > 0) {
            item.width.toFloat() / item.height
        } else 16f / 9f
        val aspect = rawAspect.coerceIn(0.45f, 2.35f)
        fun action(what: Int, icon: Int, title: String): RemoteAction {
            val pi = PendingIntent.getBroadcast(
                context, what,
                Intent(PIP_ACTION).setPackage(context.packageName).putExtra(PIP_EXTRA, what),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteAction(AndroidIcon.createWithResource(context, icon), title, title, pi)
        }
        val actions = buildList {
            add(action(PIP_PREV, android.R.drawable.ic_media_previous, "Previous"))
            if (item?.isVideo == true) {
                add(
                    action(
                        PIP_PLAY_PAUSE,
                        if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                        if (playing) "Pause" else "Play",
                    ),
                )
            }
            add(action(PIP_NEXT, android.R.drawable.ic_media_next, "Next"))
        }
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational((aspect * 10_000).toInt(), 10_000))
            .setActions(actions)
            .build()
    }

    LaunchedEffect(pipMode, vPager.currentPage, playbackOn, state.items) {
        if (pipMode) {
            activity?.setPictureInPictureParams(pipParams(state.items.getOrNull(vPager.currentPage), playbackOn))
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.getIntExtra(PIP_EXTRA, -1)) {
                    PIP_PREV -> scope.launch {
                        vPager.scrollToPage((vPager.currentPage - 1).coerceAtLeast(0))
                    }
                    PIP_NEXT -> scope.launch {
                        vPager.scrollToPage((vPager.currentPage + 1).coerceAtMost(state.items.lastIndex))
                    }
                    PIP_PLAY_PAUSE -> playbackOn = !playbackOn
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(PIP_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    fun jumpToMedia(postNo: Long) {
        val index = state.items.indexOfFirst { it.postNo == postNo }
        if (index >= 0) push(ViewerEntry.Media(index))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Horizontal navigation: left = open replies of the current post, right = back.
            .pointerInput(pipMode) {
                if (pipMode) return@pointerInput
                var dragTotal = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onHorizontalDrag = { _, delta -> dragTotal += delta },
                    onDragEnd = {
                        when {
                            dragTotal < -SWIPE_COMMIT_PX -> {
                                val t = stack.last()
                                if (t is ViewerEntry.Media) {
                                    state.items.getOrNull(t.index)?.let { push(ViewerEntry.Panel(it.postNo)) }
                                }
                            }
                            // At the bottom of the stack, back-swipe leaves the viewer.
                            dragTotal > SWIPE_COMMIT_PX -> if (stack.size > 1) pop() else onClose()
                        }
                    },
                )
            },
    ) {
        // The media feed stays alive underneath; panels slide over it.
        VerticalPager(
            state = vPager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !pipMode && onMedia,
        ) { page ->
            val item = state.items[page]
            // Already-saved media plays straight from the vault file — no buffering.
            val localPath = state.savedPaths[item.fullUrl]
            if (item.isVideo) {
                VideoPage(
                    videoUri = localPath?.let { Uri.fromFile(File(it)).toString() } ?: item.fullUrl,
                    thumbnailModel = item.thumbnailUrl,
                    initialWidth = item.width,
                    initialHeight = item.height,
                    selected = vPager.currentPage == page && onMedia,
                    playing = playbackOn,
                    onTogglePlay = { playbackOn = !playbackOn },
                    muted = muted,
                    chromeVisible = chromeVisible && !pipMode && onMedia,
                    onToggleMute = { muted = !muted },
                    onToggleChrome = { chromeVisible = !chromeVisible },
                    autoAdvance = autoAdvance,
                    onEnded = {
                        scope.launch {
                            vPager.animateScrollToPage((vPager.currentPage + 1) % state.items.size)
                        }
                    },
                )
            } else {
                ImagePage(
                    model = localPath?.let { File(it) } ?: ImageRequest.Builder(context)
                        .data(item.fullUrl)
                        .crossfade(false)
                        .build(),
                    thumbnailModel = item.thumbnailUrl,
                    contentDescription = stringResource(
                        R.string.media_image_description, item.displayName, item.width, item.height,
                    ),
                    onTap = { chromeVisible = !chromeVisible },
                )
            }
        }

        // Reply panel layer, animated push/pop.
        AnimatedContent(
            targetState = top as? ViewerEntry.Panel,
            transitionSpec = {
                if (navForward) {
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
                    depth = stack.count { it is ViewerEntry.Panel },
                    state = state,
                    onOpenSubThread = { push(ViewerEntry.Panel(it)) },
                    onJumpToMedia = ::jumpToMedia,
                    onBack = ::pop,
                )
            } else {
                Box(Modifier.fillMaxSize())
            }
        }

        run {
            val item = state.items.getOrNull(vPager.currentPage)
            ViewerTopChrome(
                visible = chromeVisible && !pipMode && onMedia,
                title = item?.displayName.orEmpty(),
                subtitle = item?.let {
                    // Pending saves (queued + downloading) surface here as "↓n".
                    val pending = state.downloadStates.count { (_, s) -> s != DownloadState.FAILED }
                    buildString {
                        append("${vPager.currentPage + 1} / ${state.items.size} · ")
                        append("${FileSize.format(it.sizeBytes)} · ${it.width}×${it.height}")
                        if (pending > 0) append(" · ↓$pending")
                    }
                },
                onClose = onClose,
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                    AutoAdvanceButton(autoAdvance) { autoAdvance = !autoAdvance }
                    IconButton(onClick = {
                        activity?.enterPictureInPictureMode(pipParams(item, playbackOn))
                    }) {
                        Icon(Icons.Filled.PictureInPictureAlt, stringResource(R.string.media_pip), tint = Color.White)
                    }
                    val downloaded = item != null && item.fullUrl in state.downloadedUrls
                    val queueState = item?.let { state.downloadStates[it.fullUrl] }
                    var downloadMenu by remember { mutableStateOf(false) }
                    Box {
                    IconButton(
                        enabled = item != null,
                        onClick = {
                            item?.let { m ->
                                when {
                                    viewModel.needsStorageAccess() -> {
                                        requestAllFilesAccess(context)
                                        scope.launch { snackbar.showSnackbar(grantAccessMessage) }
                                    }
                                    // Anything with an existing state opens the manage menu.
                                    downloaded || queueState != null -> downloadMenu = true
                                    else -> viewModel.enqueueSave(m)
                                }
                            }
                        },
                    ) {
                        when {
                            downloaded -> Icon(
                                Icons.Filled.DownloadDone,
                                stringResource(R.string.media_downloaded),
                                tint = Color(0xFF81C784),
                            )
                            queueState == DownloadState.DOWNLOADING -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            queueState == DownloadState.QUEUED -> Icon(
                                Icons.Filled.Schedule,
                                stringResource(R.string.media_queued),
                                tint = Color.White.copy(alpha = 0.7f),
                            )
                            queueState == DownloadState.FAILED -> Icon(
                                Icons.Filled.Download,
                                stringResource(R.string.media_save_failed),
                                tint = Color(0xFFE57373),
                            )
                            else -> Icon(
                                Icons.Filled.Download,
                                stringResource(R.string.media_save),
                                tint = Color.White,
                            )
                        }
                    }
                    DropdownMenu(expanded = downloadMenu, onDismissRequest = { downloadMenu = false }) {
                        val url = item?.fullUrl
                        when {
                            downloaded -> {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.media_remove_download)) },
                                    onClick = {
                                        downloadMenu = false
                                        url?.let { viewModel.removeDownload(it) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.media_redownload)) },
                                    onClick = {
                                        downloadMenu = false
                                        item?.let { viewModel.redownload(it) }
                                    },
                                )
                            }
                            queueState == DownloadState.QUEUED -> DropdownMenuItem(
                                text = { Text(stringResource(R.string.media_cancel_download)) },
                                onClick = {
                                    downloadMenu = false
                                    url?.let { viewModel.cancelQueued(it) }
                                },
                            )
                            queueState == DownloadState.DOWNLOADING -> DropdownMenuItem(
                                text = { Text(stringResource(R.string.media_downloading)) },
                                enabled = false,
                                onClick = {},
                            )
                            queueState == DownloadState.FAILED -> {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.media_retry_download)) },
                                    onClick = {
                                        downloadMenu = false
                                        item?.let { viewModel.enqueueSave(it) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.media_dismiss_failed)) },
                                    onClick = {
                                        downloadMenu = false
                                        url?.let { viewModel.dismissFailed(it) }
                                    },
                                )
                            }
                        }
                    }
                    }
                    IconButton(
                        enabled = !sharing,
                        onClick = {
                            item?.let { m ->
                                scope.launch {
                                    sharing = true
                                    val ok = shareMedia(context, m)
                                    sharing = false
                                    if (!ok) snackbar.showSnackbar(shareFailedMessage)
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
            }
        }
        if (!pipMode) {
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}

/**
 * A post and everything that replies to it (transitively), as a flat sub-thread.
 * Tapping a reply's text drills into that reply's own sub-thread; tapping a
 * thumbnail jumps the viewer to that media.
 */
@Composable
private fun SubThreadPanel(
    rootPostNo: Long,
    depth: Int,
    state: MediaUiState,
    onOpenSubThread: (Long) -> Unit,
    onJumpToMedia: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val darkTheme = isSystemInDarkTheme()
    val root = state.posts[rootPostNo]
    val replies = remember(rootPostNo, state.backlinks) {
        val out = linkedSetOf<Long>()
        val queue = ArrayDeque(state.backlinks[rootPostNo].orEmpty())
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (out.add(n)) queue.addAll(state.backlinks[n].orEmpty())
        }
        out.sorted().mapNotNull { state.posts[it] }
    }
    var revealedSpoilers by remember { mutableStateOf(setOf<Int>()) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.media_back))
                }
                Text(
                    ">>$rootPostNo",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (depth > 1) {
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        "·".repeat(depth - 1),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.media_replies, replies.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = spacing.md),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(
                    start = spacing.md, end = spacing.md, top = spacing.xs, bottom = spacing.md,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                if (root != null) {
                    item(key = "root") {
                        SubThreadPost(
                            post = root,
                            state = state,
                            darkTheme = darkTheme,
                            revealedSpoilers = revealedSpoilers,
                            onOpenSubThread = onOpenSubThread,
                            onJumpToMedia = onJumpToMedia,
                            clickableBody = false,
                            onRevealSpoiler = { revealedSpoilers = revealedSpoilers + it },
                        )
                    }
                }
                items(replies.size, key = { replies[it].no }) { i ->
                    SubThreadPost(
                        post = replies[i],
                        state = state,
                        darkTheme = darkTheme,
                        revealedSpoilers = revealedSpoilers,
                        onOpenSubThread = onOpenSubThread,
                        onJumpToMedia = onJumpToMedia,
                        clickableBody = true,
                        onRevealSpoiler = { revealedSpoilers = revealedSpoilers + it },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubThreadPost(
    post: dev.stan.yotsuba.domain.model.ThreadPost,
    state: MediaUiState,
    darkTheme: Boolean,
    revealedSpoilers: Set<Int>,
    onOpenSubThread: (Long) -> Unit,
    onJumpToMedia: (Long) -> Unit,
    clickableBody: Boolean,
    onRevealSpoiler: (Int) -> Unit,
) {
    PostCard(
        post = post,
        board = state.board,
        backlinkCount = state.backlinks[post.no].orEmpty().size,
        revealedSpoilerIds = revealedSpoilers,
        revealAll = false,
        imageSpoilerRevealed = true,
        darkTheme = darkTheme,
        onBodyTap = { tap ->
            when (tap) {
                is BodyTap.Spoiler -> onRevealSpoiler(tap.id)
                is BodyTap.SameThreadQuote -> onOpenSubThread(tap.postNo)
                else -> if (clickableBody) onOpenSubThread(post.no)
            }
        },
        onThumbnailTap = { post.media?.let { onJumpToMedia(post.no) } },
        onBacklinksTap = { onOpenSubThread(post.no) },
        onCopyPostNo = { if (clickableBody) onOpenSubThread(post.no) },
        modifier = if (clickableBody) {
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onOpenSubThread(post.no) }
        } else Modifier,
    )
}

/**
 * Streams the media at [url] into [openOutput]'s stream — from Coil's disk cache when the
 * viewer already fetched it, otherwise straight from the network. Never buffers the whole
 * file in memory.
 */
private suspend fun writeMediaTo(
    context: Context,
    url: String,
    openOutput: () -> OutputStream?,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val out = openOutput() ?: return@runCatching false
        out.use { o ->
            val snapshot = SingletonImageLoader.get(context).diskCache?.openSnapshot(url)
            if (snapshot != null) {
                snapshot.use { s -> s.data.toFile().inputStream().use { it.copyTo(o) } }
            } else {
                java.net.URL(url).openStream().use { it.copyTo(o) }
            }
        }
        true
    }.getOrDefault(false)
}

/** Opens the system "All files access" toggle for this app so the vault becomes writable. */
fun requestAllFilesAccess(context: Context) {
    if (Build.VERSION.SDK_INT < 30) return
    val intent = Intent(
        AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(
            Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Returns false when the media couldn't be fetched, so the caller can surface the failure. */
private suspend fun shareMedia(context: Context, item: MediaItem): Boolean {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared_media").apply { mkdirs() }
        File(dir, item.displayName)
    }
    if (!writeMediaTo(context, item.fullUrl) { file.outputStream() }) return false
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeOf(item.ext)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
    return true
}


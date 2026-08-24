package dev.stan.yotsuba.feature.vault

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon as AndroidIcon
import android.net.Uri
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.database.entity.SavedMediaEntity
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.FileSize
import androidx.compose.material.icons.filled.Share
import dev.stan.yotsuba.feature.media.AutoAdvanceButton
import dev.stan.yotsuba.feature.media.ImagePage
import dev.stan.yotsuba.feature.media.VideoPage
import dev.stan.yotsuba.feature.media.ViewerTopChrome
import dev.stan.yotsuba.feature.media.findComponentActivity
import dev.stan.yotsuba.feature.media.mimeOf
import dev.stan.yotsuba.feature.media.requestAllFilesAccess
import java.io.File
import kotlinx.coroutines.launch

private const val UNSORTED_KEY = "_unsorted"

private const val VAULT_PIP_ACTION = "dev.stan.yotsuba.VAULT_PIP_ACTION"
private const val VAULT_PIP_EXTRA = "what"
private const val VAULT_PIP_PREV = 0
private const val VAULT_PIP_PLAY_PAUSE = 1
private const val VAULT_PIP_NEXT = 2

private val SavedMediaEntity.boardKey: String get() = board ?: UNSORTED_KEY
private val SavedMediaEntity.threadKey: Long get() = threadNo ?: 0L
private val SavedMediaEntity.isVideo: Boolean get() = ext == ".webm" || ext == ".mp4"

/** In-app explorer over the on-disk vault: boards → threads → media grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(viewModel: VaultViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spacing = LocalSpacing.current

    // Backed by the ViewModel so rotation (which swaps the adaptive nav layout and rebuilds
    // this composable) doesn't reset the explorer to the root or close the viewer.
    var boardKey by viewModel::boardKey
    var threadKey by viewModel::threadKey
    val viewing = state.entries.firstOrNull { it.url == viewModel.viewingUrl }
    var deleting by remember { mutableStateOf<SavedMediaEntity?>(null) }

    BackHandler(enabled = boardKey != null) {
        if (threadKey != null) threadKey = null else boardKey = null
    }

    val byBoard = remember(state.entries) { state.entries.groupBy { it.boardKey } }
    val threads = remember(byBoard, boardKey) {
        byBoard[boardKey].orEmpty().groupBy { it.threadKey }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            boardKey == null -> stringResource(R.string.vault_title)
                            threadKey == null ->
                                if (boardKey == UNSORTED_KEY) stringResource(R.string.vault_unsorted) else "/$boardKey/"
                            else -> threads[threadKey]?.firstOrNull()?.subject
                                ?: threadTitle(threadKey ?: 0L)
                        },
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    if (boardKey != null) {
                        IconButton(onClick = { if (threadKey != null) threadKey = null else boardKey = null }) {
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
            // Shuffle-play whatever level is on screen: everything, one board, or one thread.
            val scopeEntries = when {
                boardKey == null -> state.entries
                threadKey == null -> byBoard[boardKey].orEmpty()
                else -> threads[threadKey].orEmpty()
            }
            if (scopeEntries.isNotEmpty() && viewModel.hasStorageAccess()) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    FloatingActionButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.Shuffle, stringResource(R.string.vault_shuffle))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        ShuffleMenuItem(R.string.vault_shuffle_everything, Icons.Filled.Shuffle) {
                            menuOpen = false
                            viewModel.startShuffle(scopeEntries.map { it.url })
                        }
                        ShuffleMenuItem(R.string.vault_shuffle_videos, Icons.Filled.Movie) {
                            menuOpen = false
                            viewModel.startShuffle(scopeEntries.filter { it.isVideo }.map { it.url })
                        }
                        ShuffleMenuItem(R.string.vault_shuffle_images, Icons.Filled.Image) {
                            menuOpen = false
                            viewModel.startShuffle(scopeEntries.filterNot { it.isVideo }.map { it.url })
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !viewModel.hasStorageAccess() -> Column(
                    Modifier.align(Alignment.Center).padding(spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Text(
                        stringResource(R.string.vault_grant_explanation),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = { requestAllFilesAccess(context) }) {
                        Text(stringResource(R.string.vault_grant_button))
                    }
                }

                state.entries.isEmpty() -> Column(
                    Modifier.align(Alignment.Center).padding(spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.vault_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.vault_empty_explanation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                boardKey == null -> LazyColumn(Modifier.fillMaxSize()) {
                    items(byBoard.keys.sorted().size) { i ->
                        val key = byBoard.keys.sorted()[i]
                        val count = byBoard[key].orEmpty().size
                        ListItem(
                            headlineContent = {
                                Text(if (key == UNSORTED_KEY) stringResource(R.string.vault_unsorted) else "/$key/")
                            },
                            supportingContent = {
                                Text(pluralStringResource(R.plurals.vault_items, count, count))
                            },
                            leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                            modifier = Modifier.clickable { boardKey = key },
                        )
                    }
                }

                threadKey == null -> LazyColumn(Modifier.fillMaxSize()) {
                    val keys = threads.keys.sortedDescending()
                    items(keys.size) { i ->
                        val key = keys[i]
                        val group = threads[key].orEmpty()
                        ListItem(
                            headlineContent = {
                                Text(
                                    group.firstOrNull()?.subject
                                        ?: if (key == 0L) stringResource(R.string.vault_unsorted)
                                        else threadTitle(key),
                                    maxLines = 1,
                                )
                            },
                            supportingContent = {
                                Text(pluralStringResource(R.plurals.vault_items, group.size, group.size))
                            },
                            leadingContent = {
                                MediaThumb(group.first(), Modifier.size(56.dp))
                            },
                            modifier = Modifier.clickable { threadKey = key },
                        )
                    }
                }

                else -> MediaGrid(
                    entries = threads[threadKey].orEmpty(),
                    onOpen = { viewModel.viewingUrl = it.url },
                    onLongPress = { deleting = it },
                )
            }
        }
    }

    viewing?.let { entry ->
        val shuffled = viewModel.shuffleOrder?.let { order ->
            val byUrl = state.entries.associateBy { it.url }
            order.mapNotNull { byUrl[it] }
        }
        val group = shuffled ?: threads[threadKey].orEmpty().ifEmpty { listOf(entry) }
        VaultViewer(
            entries = group,
            initialIndex = group.indexOfFirst { it.url == entry.url }.coerceAtLeast(0),
            autoAdvance = viewModel.autoAdvance,
            onToggleAutoAdvance = { viewModel.autoAdvance = !viewModel.autoAdvance },
            onPageViewed = { viewModel.viewingUrl = it.url },
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

@Composable
private fun threadTitle(threadNo: Long) = stringResource(R.string.vault_thread_untitled, threadNo)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(
    entries: List<SavedMediaEntity>,
    onOpen: (SavedMediaEntity) -> Unit,
    onLongPress: (SavedMediaEntity) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(entries, key = { it.url }) { entry ->
            Box(
                Modifier
                    .aspectRatio(1f)
                    .combinedClickable(
                        onClick = { onOpen(entry) },
                        onLongClick = { onLongPress(entry) },
                    ),
            ) {
                MediaThumb(entry, Modifier.fillMaxSize())
                if (entry.isVideo) {
                    Icon(
                        Icons.Filled.PlayCircle,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaThumb(entry: SavedMediaEntity, modifier: Modifier = Modifier) {
    // Images decode straight from disk; videos fall back to their cached remote thumbnail.
    AsyncImage(
        model = if (entry.isVideo) entry.thumbnailUrl else File(entry.absolutePath),
        contentDescription = entry.displayName,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/**
 * Full-screen in-app viewer over a saved-thread group: vertical TikTok-style swipe feed,
 * zoomable images and looping videos playing straight from disk.
 */
@Composable
private fun VaultViewer(
    entries: List<SavedMediaEntity>,
    initialIndex: Int,
    autoAdvance: Boolean,
    onToggleAutoAdvance: () -> Unit,
    onPageViewed: (SavedMediaEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    var muted by remember { mutableStateOf(false) }
    var playbackOn by remember { mutableStateOf(true) }
    var chromeVisible by remember { mutableStateOf(true) }
    // Same auto-hide as the live thread viewer.
    LaunchedEffect(chromeVisible) {
        if (chromeVisible) {
            kotlinx.coroutines.delay(3_000)
            chromeVisible = false
        }
    }

    BackHandler { onDismiss() }

    val pager = rememberPagerState(initialPage = initialIndex) { entries.size }
    // Rotation recreates this overlay; tracking the viewed page reopens it in place.
    LaunchedEffect(pager.currentPage) {
        entries.getOrNull(pager.currentPage)?.let(onPageViewed)
    }

    // Picture-in-picture, mirroring the live thread viewer.
    var pipMode by remember { mutableStateOf(false) }
    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose {}
        val listener = Consumer<androidx.core.app.PictureInPictureModeChangedInfo> {
            pipMode = it.isInPictureInPictureMode
        }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }

    fun pipParams(entry: SavedMediaEntity?, playing: Boolean): PictureInPictureParams {
        val w = entry?.width ?: 0
        val h = entry?.height ?: 0
        val aspect = (if (w > 0 && h > 0) w.toFloat() / h else 16f / 9f).coerceIn(0.45f, 2.35f)
        fun action(what: Int, icon: Int, title: String): RemoteAction {
            val pi = PendingIntent.getBroadcast(
                context, what,
                Intent(VAULT_PIP_ACTION).setPackage(context.packageName).putExtra(VAULT_PIP_EXTRA, what),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteAction(AndroidIcon.createWithResource(context, icon), title, title, pi)
        }
        val actions = buildList {
            add(action(VAULT_PIP_PREV, android.R.drawable.ic_media_previous, "Previous"))
            if (entry?.isVideo == true) {
                add(
                    action(
                        VAULT_PIP_PLAY_PAUSE,
                        if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                        if (playing) "Pause" else "Play",
                    ),
                )
            }
            add(action(VAULT_PIP_NEXT, android.R.drawable.ic_media_next, "Next"))
        }
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational((aspect * 10_000).toInt(), 10_000))
            .setActions(actions)
            .build()
    }

    LaunchedEffect(pipMode, pager.currentPage, playbackOn) {
        if (pipMode) {
            activity?.setPictureInPictureParams(pipParams(entries.getOrNull(pager.currentPage), playbackOn))
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.getIntExtra(VAULT_PIP_EXTRA, -1)) {
                    VAULT_PIP_PREV -> scope.launch {
                        pager.scrollToPage((pager.currentPage - 1).coerceAtLeast(0))
                    }
                    VAULT_PIP_NEXT -> scope.launch {
                        pager.scrollToPage((pager.currentPage + 1).coerceAtMost(entries.lastIndex))
                    }
                    VAULT_PIP_PLAY_PAUSE -> playbackOn = !playbackOn
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(VAULT_PIP_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !pipMode,
            ) { page ->
                val entry = entries[page]
                if (entry.isVideo) {
                    VideoPage(
                        videoUri = Uri.fromFile(File(entry.absolutePath)).toString(),
                        thumbnailModel = entry.thumbnailUrl,
                        initialWidth = entry.width ?: 0,
                        initialHeight = entry.height ?: 0,
                        selected = pager.currentPage == page,
                        playing = playbackOn,
                        onTogglePlay = { playbackOn = !playbackOn },
                        muted = muted,
                        chromeVisible = chromeVisible && !pipMode,
                        onToggleMute = { muted = !muted },
                        onToggleChrome = { chromeVisible = !chromeVisible },
                        autoAdvance = autoAdvance,
                        onEnded = {
                            scope.launch {
                                pager.animateScrollToPage((pager.currentPage + 1) % entries.size)
                            }
                        },
                    )
                } else {
                    ImagePage(
                        model = File(entry.absolutePath),
                        thumbnailModel = null,
                        contentDescription = entry.displayName,
                        onTap = { chromeVisible = !chromeVisible },
                    )
                }
            }
            run {
                val entry = entries[pager.currentPage]
                ViewerTopChrome(
                    visible = chromeVisible && !pipMode,
                    title = entry.displayName,
                    subtitle = buildString {
                        append("${pager.currentPage + 1} / ${entries.size}")
                        entry.sizeBytes?.let { append(" · ${FileSize.format(it)}") }
                        if ((entry.width ?: 0) > 0) append(" · ${entry.width}×${entry.height}")
                        entry.subject?.let { append(" · $it") }
                    },
                    onClose = onDismiss,
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    AutoAdvanceButton(autoAdvance, onToggleAutoAdvance)
                    IconButton(onClick = {
                        activity?.enterPictureInPictureMode(pipParams(entry, playbackOn))
                    }) {
                        Icon(Icons.Filled.PictureInPictureAlt, stringResource(R.string.media_pip), tint = Color.White)
                    }
                    IconButton(onClick = {
                        val file = File(entry.absolutePath)
                        val uri = FileProvider.getUriForFile(
                            context, context.packageName + ".fileprovider", file,
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = mimeOf(entry.ext.orEmpty())
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(Intent.createChooser(intent, null)) }
                    }) {
                        Icon(Icons.Filled.Share, stringResource(R.string.thread_share), tint = Color.White)
                    }
                }
            }
    }
}

@Composable
private fun ShuffleMenuItem(labelRes: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

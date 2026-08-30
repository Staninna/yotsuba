package dev.stan.yotsuba.feature.vault

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.TextField
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.OnResumeEffect
import dev.stan.yotsuba.core.designsystem.component.TabChrome
import dev.stan.yotsuba.core.designsystem.component.TabScaffoldSlots
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultLocation
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ListItem
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.feature.media.ThreadMediaViewer
import dev.stan.yotsuba.feature.media.ViewerBehaviour
import dev.stan.yotsuba.feature.media.FramePickerSheet
import dev.stan.yotsuba.feature.media.LocalMediaFeed
import dev.stan.yotsuba.feature.media.ReverseSearchSheet
import dev.stan.yotsuba.feature.media.ReverseSearchTarget
import dev.stan.yotsuba.feature.media.ViewerMenuItem
import dev.stan.yotsuba.feature.media.remoteImageUrl
import dev.stan.yotsuba.feature.media.ViewerThread
import dev.stan.yotsuba.feature.media.ViewerPage
import dev.stan.yotsuba.core.designsystem.rememberMotionSpec
import dev.stan.yotsuba.core.designsystem.token.LocalMotion
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.feature.media.shareMediaFile
import java.io.File
import kotlinx.coroutines.launch

/** In-app explorer over the on-disk vault: boards → threads → media grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    slots: TabScaffoldSlots,
    onOpenSettings: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
    /** Leaves the vault for the live thread; [postNo] scrolls to that post when given. */
    onOpenThread: (board: String, threadNo: Long, postNo: Long?) -> Unit = { _, _, _ -> },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val behaviour by viewModel.behaviour.collectAsStateWithLifecycle()
    val viewerThread by viewModel.viewerThread.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = slots.snackbar
    val resources = context.resources
    // Read and written by a BackHandler below, so it stays here rather than in the bar.
    var searchOpen by remember { mutableStateOf(state.query.isNotEmpty()) }
    var statsOpen by remember { mutableStateOf(false) }
    var dedupOpen by remember { mutableStateOf(false) }

    state.notice?.let { notice ->
        val message = when (notice) {
            VaultNotice.ImportEmpty -> stringResource(R.string.vault_import_empty)
            is VaultNotice.ImportFailed -> stringResource(R.string.vault_import_failed)
            VaultNotice.Deleted -> stringResource(R.string.vault_deleted)
            is VaultNotice.DeleteFailed ->
                stringResource(R.string.vault_delete_failed, notice.entry.displayName)
            VaultNotice.Restored -> stringResource(R.string.vault_restored)
            VaultNotice.Renamed -> stringResource(R.string.vault_renamed)
            VaultNotice.Merged -> stringResource(R.string.vault_merged)
            is VaultNotice.EditFailed -> stringResource(R.string.vault_edit_failed)
            is VaultNotice.SavedToGallery -> buildString {
                append(pluralStringResource(R.plurals.vault_saved_to_gallery, notice.count, notice.count))
                if (notice.failed > 0) append(" · ").append(stringResource(R.string.vault_saved_to_gallery_failed, notice.failed))
            }
        }
        LaunchedEffect(notice) {
            snackbar.showSnackbar(message)
            viewModel.noticeShown()
        }
    }

    // The undo snackbar lives as long as the trash window: the VM closes it by clearing
    // `undo`, which cancels this effect and dismisses the snackbar with it.
    state.undo?.let { trashed ->
        val message = resources.getQuantityString(R.plurals.vault_trashed, trashed.size, trashed.size)
        val undoLabel = stringResource(R.string.vault_undo)
        LaunchedEffect(trashed) {
            try {
                val result = snackbar.showSnackbar(message, undoLabel, duration = SnackbarDuration.Indefinite)
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
            } finally {
                snackbar.currentSnackbarData?.dismiss()
            }
        }
    }

    val syncNothing = stringResource(R.string.vault_sync_nothing)
    val syncRateLimited = stringResource(R.string.vault_sync_rate_limited)
    val rescanDone = stringResource(R.string.vault_rescan_done)

    fun reportSync(summary: VaultSyncSummary) {
        val message = when {
            summary.checked == 0 -> syncNothing
            summary.rateLimited -> syncRateLimited
            else -> resources.getQuantityString(
                R.plurals.vault_sync_done, summary.updated, summary.updated, summary.gone,
            )
        }
        scope.launch { snackbar.showSnackbar(message) }
    }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importLocalThread {
                VaultImport(
                    name = resources.getString(R.string.vault_import_default_name, uris.size),
                    sources = ImportPicker.sourcesFrom(context, uris),
                )
            }
        }
    }
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { tree ->
        if (tree != null) {
            viewModel.importLocalThread {
                VaultImport(
                    name = ImportPicker.treeName(context, tree)
                        ?: resources.getString(R.string.vault_import_default_name_plain),
                    sources = ImportPicker.sourcesFromTree(context, tree),
                )
            }
        }
    }

    OnResumeEffect(viewModel::refreshStorageAccess)
    // Back peels layers innermost first. The last enabled BackHandler composed wins, so the
    // order here is the precedence: the viewer's own reply panel (composed later still),
    // the viewer, search, a selection, then the drill-down. Search sits below selection
    // because a selection can be made while searching and should clear first.
    BackHandler(enabled = state.selection.board != null) { viewModel.navigateUp() }
    BackHandler(enabled = searchOpen && !state.selecting) {
        searchOpen = false
        viewModel.setQuery("")
    }
    BackHandler(enabled = state.selecting) { viewModel.clearSelection() }
    BackHandler(enabled = state.viewer != null) { viewModel.closeViewer() }

    Box(Modifier.fillMaxSize()) {
        TabChrome(
            slots = slots,
            topBar = {
                if (state.viewer != null) {
                    // The viewer is fullscreen; an app bar above it would be a lie.
                } else if (state.selecting) {
                    SelectionTopBar(
                        count = state.selected.size,
                        onClear = viewModel::clearSelection,
                        onShare = { shareVaultEntries(context, state.selectedEntries) },
                        onSaveToGallery = viewModel::exportSelected,
                        onDelete = viewModel::deleteSelected,
                    )
                } else if (searchOpen) {
                    SearchTopBar(
                        query = state.query,
                        onQuery = viewModel::setQuery,
                        onClose = { searchOpen = false; viewModel.setQuery("") },
                    )
                } else {
                    VaultBrowseTopBar(
                        state = state,
                        actions = VaultBarActions(
                            onNavigateUp = viewModel::navigateUp,
                            onSearch = { searchOpen = true },
                            onImportFiles = { pickFiles.launch(arrayOf("image/*", "video/*")) },
                            onImportFolder = { pickFolder.launch(null) },
                            onRescan = { viewModel.rescan { scope.launch { snackbar.showSnackbar(rescanDone) } } },
                            onFetchReplies = { viewModel.fetchReplies(::reportSync) },
                            onStats = { statsOpen = true },
                            onDedup = { dedupOpen = true },
                            onOpenSettings = onOpenSettings,
                        ),
                    )
                }
            },
            floatingActionButton = {
                // The viewer covers the whole tab; a FAB floating over it would be a stray.
                if (state.viewer == null && state.scopeEntries.isNotEmpty() && state.hasStorageAccess) {
                    VaultShuffleFab(state.scopeEntries) { viewModel.startShuffle(it) }
                }
            },
        )
        Box(Modifier.fillMaxSize()) {
            VaultExplorer(
                state = state,
                onOpenBoard = viewModel::openBoard,
                onOpenThread = viewModel::openThread,
                onOpenEntry = { viewModel.openViewer(it.url) },
                onLongPressEntry = viewModel::inspect,
                onToggleSelected = viewModel::toggleSelected,
                onDeleteThread = viewModel::deleteThread,
                onDeleteBoard = viewModel::deleteBoard,
                onRenameThread = viewModel::requestRename,
                onMergeThread = viewModel::requestMerge,
                onSort = viewModel::setSort,
                onToggleReversed = viewModel::toggleReversed,
                onFilter = viewModel::setFilter,
                onAudio = viewModel::setAudio,
                onMode = viewModel::setMode,
            )
        }

        // The viewer is an overlay, not a route: a short fade in and out, with the last
        // viewer state kept through the fade-out.
        var shownViewer by remember { mutableStateOf(state.viewer) }
        if (state.viewer != null) shownViewer = state.viewer
        val motion = LocalMotion.current
        AnimatedVisibility(
            visible = state.viewer != null,
            enter = fadeIn(rememberMotionSpec(motion.medium)),
            exit = fadeOut(rememberMotionSpec(motion.medium)),
        ) {
            val viewer = shownViewer ?: return@AnimatedVisibility
            VaultViewer(
                viewer = viewer,
                thread = viewerThread,
                behaviour = behaviour,
                playback = playback,
                autoAdvance = viewModel.autoAdvance,
                onToggleAutoAdvance = { viewModel.autoAdvance = !viewModel.autoAdvance },
                onPageViewed = { viewModel.onViewerPage(it.url) },
                onDismiss = { viewModel.closeViewer() },
                onDelete = { viewModel.requestDelete(it, undoable = false) },
                onOpenThread = onOpenThread,
            )
        }
    }

    if (statsOpen) {
        val stats by viewModel.stats.collectAsStateWithLifecycle()
        VaultStatsSheet(
            stats = stats,
            onDismiss = { statsOpen = false },
            onOpenThread = { location ->
                statsOpen = false
                viewModel.reveal(location)
            },
        )
    }

    if (dedupOpen) {
        VaultDedupSheet(
            onDismiss = { dedupOpen = false },
            onNotice = { message -> scope.launch { snackbar.showSnackbar(message) } },
        )
    }

    state.inspecting?.let { entry ->
        VaultEntrySheet(
            entry = entry,
            onDismiss = viewModel::closeInspector,
            onSelect = { viewModel.closeInspector(); viewModel.toggleSelected(entry) },
            onOpenThread = { postNo ->
                viewModel.closeInspector()
                onOpenThread(entry.location.board, entry.location.threadNo, postNo)
            },
            onShare = { viewModel.closeInspector(); shareVaultEntries(context, listOf(entry)) },
            onSaveToGallery = { viewModel.closeInspector(); viewModel.exportToGallery(listOf(entry)) },
            onDelete = { viewModel.closeInspector(); viewModel.requestDelete(entry, undoable = true) },
        )
    }

    when (val edit = state.threadEdit) {
        is VaultThreadEdit.Rename -> RenameDialog(
            current = state.openBoard?.threads?.firstOrNull { it.location == edit.location }?.subject.orEmpty(),
            onConfirm = viewModel::rename,
            onCancel = viewModel::cancelThreadEdit,
        )
        is VaultThreadEdit.Merge -> MergeDialog(
            targets = state.mergeTargets,
            onConfirm = viewModel::merge,
            onCancel = viewModel::cancelThreadEdit,
        )
        null -> Unit
    }

    state.deleting?.let { request ->
        VaultDeleteDialog(
            request = request,
            onConfirm = viewModel::confirmDelete,
            onCancel = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun RenameDialog(current: String, onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.vault_rename_thread)) },
        text = {
            TextField(value = name, onValueChange = { name = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) {
                Text(stringResource(R.string.vault_rename))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.vault_cancel)) } },
    )
}

/** Picks the thread the queued one is folded into. */
@Composable
private fun MergeDialog(
    targets: List<VaultThreadSection>,
    onConfirm: (VaultLocation) -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.vault_merge_into)) },
        text = {
            if (targets.isEmpty()) {
                Text(stringResource(R.string.vault_merge_none))
            } else {
                LazyColumn {
                    items(targets.size, key = { targets[it].location.threadNo }) { i ->
                        val section = targets[i]
                        ListItem(
                            headlineContent = { Text(threadTitle(section.location, section.subject), maxLines = 1) },
                            supportingContent = {
                                val count = section.entries.size
                                Text(pluralStringResource(R.plurals.vault_items, count, count))
                            },
                            modifier = Modifier.clickable { onConfirm(section.location) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.vault_cancel)) } },
    )
}

/** Confirmation with a "don't ask again" box that turns the setting off for good. */
@Composable
private fun VaultDeleteDialog(
    request: VaultDeleteRequest,
    onConfirm: (dontAskAgain: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var dontAsk by remember { mutableStateOf(false) }
    val count = request.entries.size
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.vault_delete_title)) },
        text = {
            Column {
                Text(
                    request.single?.let { stringResource(R.string.vault_delete_body, it.displayName) }
                        ?: pluralStringResource(R.plurals.vault_delete_many_body, count, count),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = spacing.sm).clickable { dontAsk = !dontAsk },
                ) {
                    Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                    Text(stringResource(R.string.vault_delete_dont_ask))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(dontAsk) }) { Text(stringResource(R.string.vault_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.vault_cancel)) }
        },
    )
}

/**
 * Full-screen in-app viewer over a saved-thread group: vertical TikTok-style swipe feed,
 * zoomable images and looping videos playing straight from disk.
 */
@Composable
private fun VaultViewer(
    viewer: VaultViewerState,
    thread: ViewerThread,
    behaviour: ViewerBehaviour,
    playback: VaultPlayback,
    autoAdvance: Boolean,
    onToggleAutoAdvance: () -> Unit,
    onPageViewed: (VaultEntry) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (VaultEntry) -> Unit,
    onOpenThread: (board: String, threadNo: Long, postNo: Long?) -> Unit,
) {
    val context = LocalContext.current
    val entries = viewer.entries
    var searchTarget by remember { mutableStateOf<ReverseSearchTarget?>(null) }
    var frameSource by remember { mutableStateOf<Pair<File, Long>?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val searchFailedMessage = stringResource(R.string.media_search_open_failed)
    val frameFailedMessage = stringResource(R.string.media_frame_failed)

    ThreadMediaViewer(
        pages = entries.map { it.toViewerPage() },
        thread = thread,
        behaviour = behaviour,
        initialIndex = viewer.index,
        muted = playback.muted,
        playing = playback.playing,
        autoAdvance = autoAdvance,
        onToggleAutoAdvance = onToggleAutoAdvance,
        postNoAt = { page -> entries.getOrNull(page)?.postNo },
        indexOfPost = { postNo -> entries.indexOfFirst { it.postNo == postNo } },
        onPageViewed = { page -> entries.getOrNull(page)?.let(onPageViewed) },
        onDismiss = onDismiss,
        topBarMenu = { page, close ->
            val current = entries.getOrNull(page)
            ViewerMenuItem(Icons.Filled.Delete, stringResource(R.string.vault_delete)) {
                close()
                current?.let(onDelete)
            }
            if (current != null && current.location.isRemote) {
                ViewerMenuItem(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.vault_open_thread)) {
                    close()
                    onDismiss()
                    onOpenThread(current.location.board, current.location.threadNo, current.postNo)
                }
            }
            val feed = LocalMediaFeed.current
            if (current != null && current.isVideo) {
                ViewerMenuItem(Icons.Filled.ImageSearch, stringResource(R.string.media_search_frame)) {
                    close()
                    frameSource = File(current.absolutePath) to (feed?.videoPositionMs ?: 0L)
                }
            } else if (current != null) {
                ViewerMenuItem(Icons.Filled.ImageSearch, stringResource(R.string.media_search_image)) {
                    close()
                    searchTarget = ReverseSearchTarget(
                        remoteUrl = remoteImageUrl(current.url),
                        file = File(current.absolutePath),
                        ext = current.ext.orEmpty(),
                    )
                }
            }
        },
        overlay = {
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        },
    ) { page, openReplies ->
        val postNo = entries.getOrNull(page)?.postNo
        if (thread.hasPosts && postNo != null) {
            val replies = thread.graph.descendantsOf(postNo).size
            IconButton(onClick = { openReplies(postNo) }) {
                BadgedBox(badge = { if (replies > 0) Badge { Text(replies.toString()) } }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Comment,
                        stringResource(R.string.vault_replies),
                        tint = Color.White,
                    )
                }
            }
        }
        IconButton(onClick = {
            entries.getOrNull(page)?.let { entry ->
                shareMediaFile(context, File(entry.absolutePath), entry.ext.orEmpty())
            }
        }) {
            Icon(Icons.Filled.Share, stringResource(R.string.thread_share), tint = Color.White)
        }
    }

    frameSource?.let { (video, at) ->
        FramePickerSheet(
            video = video,
            startMs = at,
            onPick = { frame ->
                frameSource = null
                searchTarget = ReverseSearchTarget(remoteUrl = null, file = frame, ext = ".jpg")
            },
            onDismiss = { frameSource = null },
            onFailed = { scope.launch { snackbar.showSnackbar(frameFailedMessage) } },
        )
    }
    searchTarget?.let { target ->
        ReverseSearchSheet(
            target = target,
            onDismiss = { searchTarget = null },
            onFailed = { scope.launch { snackbar.showSnackbar(searchFailedMessage) } },
        )
    }
}

private fun VaultEntry.toViewerPage(): ViewerPage = if (isVideo) {
    ViewerPage.Video(
        uri = Uri.fromFile(File(absolutePath)).toString(),
        thumbnailModel = localThumbnailPath?.let(::File) ?: thumbnailUrl,
        width = width ?: 0,
        height = height ?: 0,
        sizeBytes = sizeBytes,
        title = displayName,
        note = subject,
        contentDescription = displayName,
    )
} else {
    ViewerPage.Image(
        model = File(absolutePath),
        thumbnailModel = File(absolutePath),
        width = width ?: 0,
        height = height ?: 0,
        sizeBytes = sizeBytes,
        title = displayName,
        note = subject,
        contentDescription = displayName,
    )
}

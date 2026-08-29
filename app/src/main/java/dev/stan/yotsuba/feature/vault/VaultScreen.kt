package dev.stan.yotsuba.feature.vault

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.OnResumeEffect
import dev.stan.yotsuba.core.designsystem.component.TabChrome
import dev.stan.yotsuba.core.designsystem.component.TabScaffoldSlots
import dev.stan.yotsuba.core.util.FileSize
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultLocation
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ListItem
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.feature.media.ThreadMediaViewer
import dev.stan.yotsuba.feature.media.ViewerBehaviour
import dev.stan.yotsuba.feature.media.ViewerThread
import dev.stan.yotsuba.feature.media.ViewerPage
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
    var importMenuOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(state.query.isNotEmpty()) }
    var syncMenuOpen by remember { mutableStateOf(false) }
    var statsOpen by remember { mutableStateOf(false) }

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
            viewModel.importLocalThread(
                name = defaultImportName(uris.size),
                sources = ImportPicker.sourcesFrom(context, uris),
            )
        }
    }
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { tree ->
        if (tree != null) {
            viewModel.importLocalThread(
                name = ImportPicker.treeName(context, tree) ?: defaultImportName(0),
                sources = ImportPicker.sourcesFromTree(context, tree),
            )
        }
    }


    OnResumeEffect(viewModel::refreshStorageAccess)
    // Back peels layers in order: the viewer's own reply panel (its BackHandler is composed
    // later, so it wins while enabled), then the viewer, then the drill-down.
    BackHandler(enabled = state.selection.board != null && state.viewer == null) { viewModel.navigateUp() }
    BackHandler(enabled = state.selecting && state.viewer == null) { viewModel.clearSelection() }
    BackHandler(enabled = searchOpen && !state.selecting && state.viewer == null) {
        searchOpen = false
        viewModel.setQuery("")
    }
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
                } else TopAppBar(
                    title = {
                        Column {
                            Text(vaultTitle(state), maxLines = 1)
                            if (state.hasStorageAccess && state.entries.isNotEmpty()) {
                                Text(
                                    vaultSubtitle(state),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (state.selection.board != null) {
                            IconButton(onClick = { viewModel.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.media_back))
                            }
                        }
                    },
                    actions = {
                        if (state.hasStorageAccess && state.entries.isNotEmpty()) {
                            IconButton(onClick = { searchOpen = true }) {
                                Icon(Icons.Filled.Search, stringResource(R.string.vault_search))
                            }
                        }
                        if (state.hasStorageAccess) {
                            Box {
                                IconButton(
                                    enabled = !state.importing,
                                    onClick = { importMenuOpen = true },
                                ) {
                                    Icon(Icons.Filled.Add, stringResource(R.string.vault_import))
                                }
                                DropdownMenu(
                                    expanded = importMenuOpen,
                                    onDismissRequest = { importMenuOpen = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.vault_import_files)) },
                                        onClick = {
                                            importMenuOpen = false
                                            pickFiles.launch(arrayOf("image/*", "video/*"))
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.vault_import_folder)) },
                                        onClick = {
                                            importMenuOpen = false
                                            pickFolder.launch(null)
                                        },
                                    )
                                }
                            }
                        }
                        if (state.sync.running) {
                            // The counter matters: a rate-limited sync of many threads
                            // takes about a second each, and a bare spinner looks hung.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (state.sync.total > 0) {
                                    Text(
                                        stringResource(
                                            R.string.vault_sync_progress,
                                            state.sync.done,
                                            state.sync.total,
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                            }
                        } else if (state.hasStorageAccess) {
                            Box {
                                IconButton(onClick = { syncMenuOpen = true }) {
                                    Icon(Icons.Filled.Refresh, stringResource(R.string.vault_sync))
                                }
                                DropdownMenu(
                                    expanded = syncMenuOpen,
                                    onDismissRequest = { syncMenuOpen = false },
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            MenuLabel(R.string.vault_rescan_label, R.string.vault_rescan_explanation)
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                        onClick = {
                                            syncMenuOpen = false
                                            viewModel.rescan { scope.launch { snackbar.showSnackbar(rescanDone) } }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            MenuLabel(R.string.vault_fetch_replies, R.string.vault_fetch_replies_explanation)
                                        },
                                        leadingIcon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
                                        onClick = {
                                            syncMenuOpen = false
                                            viewModel.fetchReplies { summary -> reportSync(summary) }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            MenuLabel(R.string.vault_stats_label, R.string.vault_stats_explanation)
                                        },
                                        leadingIcon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                                        onClick = {
                                            syncMenuOpen = false
                                            statsOpen = true
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Outlined.Settings, stringResource(R.string.action_open_settings))
                        }
                    },
                )
            },
            floatingActionButton = {
                if (state.scopeEntries.isNotEmpty() && state.hasStorageAccess) {
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
                onFilter = viewModel::setFilter,
                onMode = viewModel::setMode,
            )
        }

        state.viewer?.let { viewer ->
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
                viewModel.openBoard(location.board)
                viewModel.openThread(location)
            },
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

/** A menu entry that says what it does underneath its name. */
@Composable
private fun MenuLabel(titleRes: Int, explanationRes: Int) {
    Column {
        Text(stringResource(titleRes))
        Text(
            stringResource(explanationRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The top bar as a search field; the query lives in the VM so rotation keeps it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text(stringResource(R.string.vault_search_hint)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.media_back))
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.vault_search_clear))
                }
            }
        },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Replaces the top bar while items are ticked: the count, and what can be done with them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClear: () -> Unit,
    onShare: () -> Unit,
    onSaveToGallery: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.vault_selected_count, count, count)) },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, stringResource(R.string.vault_clear_selection))
            }
        },
        actions = {
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, stringResource(R.string.thread_share)) }
            IconButton(onClick = onSaveToGallery) {
                Icon(Icons.Filled.SaveAlt, stringResource(R.string.vault_save_to_gallery))
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.vault_delete)) }
        },
    )
}

/** Confirmation with a "don't ask again" box that turns the setting off for good. */
@Composable
private fun VaultDeleteDialog(
    request: VaultDeleteRequest,
    onConfirm: (dontAskAgain: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
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
                    modifier = Modifier.padding(top = 8.dp).clickable { dontAsk = !dontAsk },
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
        IconButton(onClick = { entries.getOrNull(page)?.let(onDelete) }) {
            Icon(Icons.Filled.Delete, stringResource(R.string.vault_delete), tint = Color.White)
        }
        val current = entries.getOrNull(page)
        if (current != null && current.location.isRemote) {
            IconButton(onClick = {
                onDismiss()
                onOpenThread(current.location.board, current.location.threadNo, current.postNo)
            }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.vault_open_thread), tint = Color.White)
            }
        }
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
        width = width ?: 0,
        height = height ?: 0,
        sizeBytes = sizeBytes,
        title = displayName,
        note = subject,
        contentDescription = displayName,
    )
}

@Composable
private fun vaultTitle(state: VaultUiState): String = when {
    state.selection.board == null -> stringResource(R.string.vault_title)
    state.selection.thread == null -> boardTitle(state.selection.board!!)
    else -> threadTitle(state.selection.thread!!, state.openThread?.subject)
}

/** Item count and disk use of whatever level is on screen. */
@Composable
private fun vaultSubtitle(state: VaultUiState): String {
    val entries = state.scopeEntries
    return pluralStringResource(R.plurals.vault_items, entries.size, entries.size) +
        " · " + FileSize.format(entries.totalBytes)
}

/** Fallback thread name when the picker gives files but no folder to name them after. */
private fun defaultImportName(count: Int): String =
    if (count > 0) "Imported ($count)" else "Imported"

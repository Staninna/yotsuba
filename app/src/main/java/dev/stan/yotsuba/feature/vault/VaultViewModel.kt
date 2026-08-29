package dev.stan.yotsuba.feature.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.MediaAutoplay
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.feature.media.ViewerBehaviour
import dev.stan.yotsuba.feature.media.ViewerThread
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** How long a grid delete can be undone before the trash is emptied. */
const val UNDO_WINDOW_MS = 30_000L

/** Explorer drill-down position: root → board → thread. */
data class VaultSelection(
    val board: String? = null,
    val thread: VaultLocation? = null,
)

/** One thread directory's entries. The subject is whatever the newest row recorded. */
data class VaultThreadSection(
    val location: VaultLocation,
    val subject: String?,
    val entries: List<VaultEntry>,
)

data class VaultBoardSection(
    val board: String,
    val threads: List<VaultThreadSection>,
    val entries: List<VaultEntry>,
)

/**
 * How the viewer starts a video, from the same settings the live viewer reads. Playback is
 * from disk, so "autoplay on unmetered networks only" means autoplay: no network is used.
 * Sound follows the board: a webm from a board without audio starts muted, as it would live.
 */
data class VaultPlayback(val muted: Boolean = true, val playing: Boolean = true)

/** Entries-in-order plus the index of the one on screen. */
data class VaultViewerState(val entries: List<VaultEntry>, val index: Int) {
    val current: VaultEntry get() = entries[index]
}

/** One-shot messages for the screen to show; it calls [VaultViewModel.noticeShown] after. */
sealed interface VaultNotice {
    data object ImportEmpty : VaultNotice
    data class ImportFailed(val error: VaultError) : VaultNotice
    data object Deleted : VaultNotice
    data class DeleteFailed(val entry: VaultEntry, val error: VaultError) : VaultNotice
    data object Restored : VaultNotice
    data class SavedToGallery(val count: Int, val failed: Int) : VaultNotice
}

/** Entries queued for deletion and whether the grid's undo window applies to them. */
data class VaultDeleteRequest(val entries: List<VaultEntry>, val undoable: Boolean) {
    val single: VaultEntry? get() = entries.singleOrNull()
}

/** Progress of a vault sync: local rebuild, then one live thread at a time. */
data class VaultSyncState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
)

data class VaultUiState(
    /** Entries with a real file on disk, newest first. */
    val entries: List<VaultEntry> = emptyList(),
    val boards: List<VaultBoardSection> = emptyList(),
    val selection: VaultSelection = VaultSelection(),
    val viewer: VaultViewerState? = null,
    val sync: VaultSyncState = VaultSyncState(),
    /** False until "All files access" is granted; the explorer shows the grant prompt instead. */
    val hasStorageAccess: Boolean = false,
    /** True while an import is copying, so the screen blocks a second one. */
    val importing: Boolean = false,
    val notice: VaultNotice? = null,
    /** The delete whose confirmation is on screen; here so rotation keeps the dialog. */
    val deleting: VaultDeleteRequest? = null,
    /** Entries sitting in the trash behind an Undo snackbar; null once the window closes. */
    val undo: List<VaultEntry>? = null,
    /** URLs ticked in multi-select; empty means not selecting. */
    val selected: Set<String> = emptySet(),
) {
    val selecting: Boolean get() = selected.isNotEmpty()
    val selectedEntries: List<VaultEntry> get() = entries.filter { it.url in selected }

    val openBoard: VaultBoardSection? get() = boards.firstOrNull { it.board == selection.board }
    val openThread: VaultThreadSection?
        get() = openBoard?.threads?.firstOrNull { it.location == selection.thread }

    /** Whatever level is on screen: everything, one board, or one thread. */
    val scopeEntries: List<VaultEntry>
        get() = when {
            selection.board == null -> entries
            selection.thread == null -> openBoard?.entries.orEmpty()
            else -> openThread?.entries.orEmpty()
        }
}

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val mediaVault: MediaVaultRepository,
    private val settingsRepository: SettingsRepository,
    private val boardRepository: BoardRepository,
) : ViewModel() {

    init {
        // Whatever the previous process left in the trash has outlived its undo window.
        viewModelScope.launch { mediaVault.purgeTrash() }
    }

    /** Playback preferences for the full-screen viewer. Saving does not apply here. */
    val behaviour: StateFlow<ViewerBehaviour> = settingsRepository.settings
        .map {
            ViewerBehaviour(
                keepScreenOn = it.keepScreenOnWhileWatching,
                doubleTapSeek = it.doubleTapSeekEnabled,
                seekStepSeconds = it.seekStep.seconds,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewerBehaviour())

    private val syncState = MutableStateFlow(VaultSyncState())

    // Explorer position and viewer live here, not in the composable: the adaptive layout
    // switch on rotation (nav bar ↔ nav rail) rebuilds the screen and would wipe
    // remembered state.
    private val selection = MutableStateFlow(VaultSelection())
    /** URL of the entry open in the full-screen viewer, or null. */
    private val viewingUrl = MutableStateFlow<String?>(null)
    /**
     * When set, the viewer plays this url order instead of the current thread — the result of
     * a shuffle button. Reshuffled on every press; kept here so rotation replays the same order.
     */
    private val shuffleOrder = MutableStateFlow<List<String>?>(null)

    /** Viewer toggle: advance to the next item when a video ends instead of looping it. */
    var autoAdvance by mutableStateOf(false)

    private val importing = MutableStateFlow(false)
    private val notice = MutableStateFlow<VaultNotice?>(null)
    private val deleting = MutableStateFlow<VaultDeleteRequest?>(null)
    private val undo = MutableStateFlow<List<VaultEntry>?>(null)
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private var undoWindow: Job? = null

    /**
     * Copies the picked files into a new local thread. The explorer refreshes itself: the
     * DB rows land as part of the import. Failure surfaces as a [VaultNotice].
     */
    fun importLocalThread(name: String, sources: List<ImportSource>) {
        if (importing.value) return
        if (sources.isEmpty()) {
            notice.value = VaultNotice.ImportEmpty
            return
        }
        viewModelScope.launch {
            importing.value = true
            try {
                mediaVault.importLocalThread(name, sources)?.let { notice.value = VaultNotice.ImportFailed(it) }
            } finally {
                importing.value = false
            }
        }
    }

    fun noticeShown() {
        notice.value = null
    }

    /** The viewer's inputs, folded into one so the outer combine stays within five flows. */
    private data class Viewing(val url: String?, val shuffle: List<String>?)

    private val viewing = combine(viewingUrl, shuffleOrder) { url, shuffle -> Viewing(url, shuffle) }

    /** Transient screen-level flags, folded for the same reason. */
    private data class Activity(
        val importing: Boolean,
        val notice: VaultNotice?,
        val access: Boolean,
        val deleting: VaultDeleteRequest?,
        val undo: List<VaultEntry>?,
    )

    private val activity = combine(importing, notice, mediaVault.storageAccess, deleting, undo) { i, n, a, d, u ->
        Activity(i, n, a, d, u)
    }

    /** Everything the user is in the middle of editing on the explorer itself. */
    private data class Editing(val selected: Set<String>)

    private val editing = selected.map { Editing(it) }

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<VaultUiState> = combine(
        mediaVault.entries(), syncState, selection, viewing, activity, editing,
    ) { args ->
        val entries = args[0] as List<VaultEntry>
        val sync = args[1] as VaultSyncState
        val sel = args[2] as VaultSelection
        val viewing = args[3] as Viewing
        val activity = args[4] as Activity
        val editing = args[5] as Editing
        VaultUiState(
            entries = entries,
            boards = groupByBoard(entries),
            selection = sel,
            viewer = viewerState(entries, sel, viewing.url, viewing.shuffle),
            sync = sync,
            hasStorageAccess = activity.access,
            importing = activity.importing,
            notice = activity.notice,
            deleting = activity.deleting,
            undo = activity.undo,
            // Selection follows the entries: a deleted or rescanned-away file drops out.
            selected = editing.selected.filterTo(mutableSetOf()) { url -> entries.any { it.url == url } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

    fun openBoard(board: String) = selection.update { VaultSelection(board = board) }

    fun openThread(location: VaultLocation) = selection.update { it.copy(thread = location) }

    /** One step up the drill-down: thread → board → root. */
    fun navigateUp() {
        selected.value = emptySet()
        selection.update { if (it.thread != null) it.copy(thread = null) else VaultSelection() }
    }

    // Multi-select. A thread row toggles all its entries at once, so one set of URLs
    // serves the grid and the thread list alike.

    fun toggleSelected(urls: Collection<String>) = selected.update { current ->
        if (current.containsAll(urls)) current - urls.toSet() else current + urls
    }

    fun toggleSelected(entry: VaultEntry) = toggleSelected(listOf(entry.url))

    fun clearSelection() {
        selected.value = emptySet()
    }

    fun deleteSelected() {
        val entries = uiState.value.selectedEntries
        selected.value = emptySet()
        requestDelete(entries, undoable = true)
    }

    fun deleteThread(location: VaultLocation) =
        requestDelete(uiState.value.entries.filter { it.location == location }, undoable = false)

    fun deleteBoard(board: String) =
        requestDelete(uiState.value.entries.filter { it.location.board == board }, undoable = false)

    /** Copies [entries] into the gallery and reports how many made it. */
    fun exportToGallery(entries: List<VaultEntry>) {
        if (entries.isEmpty()) return
        selected.value = emptySet()
        viewModelScope.launch {
            var failed = 0
            for (entry in entries) if (mediaVault.exportToGallery(entry.url) != null) failed++
            notice.value = VaultNotice.SavedToGallery(count = entries.size - failed, failed = failed)
        }
    }

    fun exportSelected() = exportToGallery(uiState.value.selectedEntries)

    /**
     * The saved conversation for whichever thread the viewer is showing, keyed off the
     * page on screen so a shuffle walking across threads swaps the panel with it. Empty
     * until the sidecar is read, and empty forever for a thread saved before replies were
     * captured -- the explorer hides the affordance rather than opening a blank panel.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val viewerThread: StateFlow<ViewerThread> = combine(viewingUrl, mediaVault.entries()) { url, entries ->
        url?.let { u -> entries.firstOrNull { it.url == u }?.location }
    }
        .distinctUntilChanged()
        .mapLatest { location ->
            when {
                location == null || location.isUnsorted -> ViewerThread()
                else -> ViewerThread.of(
                    mediaVault.savedThread(location.board, location.threadNo),
                    board = if (location.isRemote) boardRepository.board(location.board) else null,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewerThread())

    val playback: StateFlow<VaultPlayback> = combine(settingsRepository.settings, viewerThread) { settings, thread ->
        VaultPlayback(
            muted = thread.board?.webmAudio != true,
            playing = settings.mediaAutoplay != MediaAutoplay.NEVER,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultPlayback())

    fun openViewer(url: String) {
        viewingUrl.value = url
    }

    /** Called as the viewer pages, so rotation reopens it in place. */
    fun onViewerPage(url: String) {
        viewingUrl.value = url
    }

    fun closeViewer() {
        viewingUrl.value = null
        shuffleOrder.value = null
    }

    /** Starts the viewer over [urls] in a fresh random order. */
    fun startShuffle(urls: List<String>) {
        if (urls.isEmpty()) return
        val order = urls.shuffled()
        shuffleOrder.value = order
        viewingUrl.value = order.first()
    }

    /** The grant happens off in system settings, so re-check whenever the screen comes back. */
    fun refreshStorageAccess() = mediaVault.refreshStorageAccess()

    /**
     * Brings the vault up to date: rebuild the local index, then refresh every saved
     * thread's comment section from the live thread while it is still there. The second
     * half is rate-limited to about one thread a second, hence the progress counter.
     */
    fun sync(onDone: (VaultSyncSummary) -> Unit = {}) {
        if (syncState.value.running) return
        viewModelScope.launch {
            syncState.value = VaultSyncState(running = true)
            mediaVault.migrateLegacyIfNeeded()
            mediaVault.rescan()
            val summary = mediaVault.syncSavedThreads { done, total ->
                syncState.value = VaultSyncState(running = true, done = done, total = total)
            }
            syncState.value = VaultSyncState()
            onDone(summary)
        }
    }

    /**
     * Queues [entries] for deletion. With the confirmation setting on, the dialog shows
     * first; off, the delete runs at once. [undoable] deletes go through the trash and stay
     * recoverable for [UNDO_WINDOW_MS]; the viewer's delete is final, the grid's is not.
     */
    fun requestDelete(entries: List<VaultEntry>, undoable: Boolean) {
        if (entries.isEmpty()) return
        val request = VaultDeleteRequest(entries, undoable)
        viewModelScope.launch {
            if (settingsRepository.settings.first().confirmVaultDelete) {
                deleting.value = request
            } else {
                delete(request)
            }
        }
    }

    fun requestDelete(entry: VaultEntry, undoable: Boolean = false) = requestDelete(listOf(entry), undoable)

    fun cancelDelete() {
        deleting.value = null
    }

    /**
     * Deletes whatever [requestDelete] queued and dismisses the dialog. [dontAskAgain]
     * flips the confirmation setting off, so the next delete skips the dialog.
     */
    fun confirmDelete(dontAskAgain: Boolean = false) {
        val request = deleting.value ?: return
        deleting.value = null
        viewModelScope.launch {
            if (dontAskAgain) settingsRepository.update { it.copy(confirmVaultDelete = false) }
            delete(request)
        }
    }

    private suspend fun delete(request: VaultDeleteRequest) {
        if (request.undoable) {
            trash(request.entries)
            return
        }
        var failed: VaultNotice? = null
        for (entry in request.entries) {
            mediaVault.delete(entry.url)?.let { failed = VaultNotice.DeleteFailed(entry, it) }
        }
        notice.value = failed ?: VaultNotice.Deleted
    }

    private suspend fun trash(entries: List<VaultEntry>) {
        // A second delete inside the window commits the first: one undo at a time.
        undoWindow?.cancel()
        mediaVault.purgeTrash()
        val moved = entries.filter { entry ->
            when (val error = mediaVault.trash(entry.url)) {
                null -> true
                else -> { notice.value = VaultNotice.DeleteFailed(entry, error); false }
            }
        }
        undo.value = moved.ifEmpty { null }
        if (moved.isEmpty()) return
        undoWindow = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            undo.value = null
            mediaVault.purgeTrash()
        }
    }

    /** Brings back whatever the last grid delete moved to the trash. */
    fun undoDelete() {
        val entries = undo.value ?: return
        undoWindow?.cancel()
        undo.value = null
        viewModelScope.launch {
            entries.forEach { mediaVault.restoreTrashed(it.url) }
            notice.value = VaultNotice.Restored
        }
    }

    /** Boards sort by directory name, which puts the `_`-prefixed reserved ones first. */
    private fun groupByBoard(entries: List<VaultEntry>): List<VaultBoardSection> =
        entries
            .groupBy { it.location.board }
            .toList()
            .sortedBy { (board, _) -> board }
            .map { (board, group) ->
                val threads = group
                    .groupBy { it.location }
                    .map { (location, threadEntries) ->
                        VaultThreadSection(
                            location = location,
                            subject = threadEntries.firstNotNullOfOrNull { it.subject },
                            entries = threadEntries,
                        )
                    }
                    .sortedByDescending { it.location.threadNo }
                VaultBoardSection(board = board, threads = threads, entries = group)
            }

    /** The viewer's play order and position; null once the viewed entry is gone (deleted). */
    private fun viewerState(
        entries: List<VaultEntry>,
        sel: VaultSelection,
        url: String?,
        shuffle: List<String>?,
    ): VaultViewerState? {
        val byUrl = entries.associateBy { it.url }
        val current = url?.let { byUrl[it] } ?: return null
        val ordered = shuffle?.mapNotNull { byUrl[it] }
            ?: entries.filter { it.location == sel.thread }.ifEmpty { listOf(current) }
        val index = ordered.indexOfFirst { it.url == current.url }.coerceAtLeast(0)
        return VaultViewerState(ordered, index)
    }
}

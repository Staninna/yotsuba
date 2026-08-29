package dev.stan.yotsuba.feature.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.domain.model.VaultSyncSummary
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
import kotlinx.coroutines.launch

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

/** Entries-in-order plus the index of the one on screen. */
data class VaultViewerState(val entries: List<VaultEntry>, val index: Int) {
    val current: VaultEntry get() = entries[index]
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
) {
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
    settingsRepository: SettingsRepository,
) : ViewModel() {

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

    /** Non-null while an import is copying, so the screen can block a second one. */
    var importing by mutableStateOf(false)
        private set

    /**
     * Copies the picked files into a new local thread. Returns the error to report, or null
     * when it worked. The explorer refreshes itself: the DB rows land as part of the import.
     */
    suspend fun importLocalThread(name: String, sources: List<ImportSource>): VaultError? {
        if (sources.isEmpty() || importing) return null
        importing = true
        return try {
            mediaVault.importLocalThread(name, sources)
        } finally {
            importing = false
        }
    }

    /** The viewer's inputs, folded into one so the outer combine stays within five flows. */
    private data class Viewing(val url: String?, val shuffle: List<String>?)

    private val viewing = combine(viewingUrl, shuffleOrder) { url, shuffle -> Viewing(url, shuffle) }

    val uiState: StateFlow<VaultUiState> = combine(
        mediaVault.entries(), syncState, selection, viewing, mediaVault.storageAccess,
    ) { entries, sync, sel, viewing, access ->
        VaultUiState(
            entries = entries,
            boards = groupByBoard(entries),
            selection = sel,
            viewer = viewerState(entries, sel, viewing.url, viewing.shuffle),
            sync = sync,
            hasStorageAccess = access,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

    fun openBoard(board: String) = selection.update { VaultSelection(board = board) }

    fun openThread(location: VaultLocation) = selection.update { it.copy(thread = location) }

    /** One step up the drill-down: thread → board → root. */
    fun navigateUp() = selection.update {
        if (it.thread != null) it.copy(thread = null) else VaultSelection()
    }

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
            if (location == null || location.isUnsorted) ViewerThread()
            else ViewerThread.of(mediaVault.savedThread(location.board, location.threadNo))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewerThread())

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

    suspend fun delete(url: String): VaultError? = mediaVault.delete(url)

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

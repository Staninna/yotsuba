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
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.feature.media.ViewerBehaviour
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which board group is open in the explorer. */
sealed interface VaultBoardKey {
    data class Board(val code: String) : VaultBoardKey
    data object Unsorted : VaultBoardKey
}

/** Explorer drill-down position: root → board → thread. */
data class VaultSelection(
    val board: VaultBoardKey? = null,
    val thread: VaultLocation? = null,
)

data class VaultThreadSection(val location: VaultLocation, val entries: List<VaultEntry>)

data class VaultBoardSection(
    val key: VaultBoardKey,
    val threads: List<VaultThreadSection>,
    val entries: List<VaultEntry>,
)

/** Entries-in-order plus the index of the one on screen. */
data class VaultViewerState(val entries: List<VaultEntry>, val index: Int) {
    val current: VaultEntry get() = entries[index]
}

data class VaultUiState(
    /** Entries with a real file on disk, newest first. */
    val entries: List<VaultEntry> = emptyList(),
    val boards: List<VaultBoardSection> = emptyList(),
    val selection: VaultSelection = VaultSelection(),
    val viewer: VaultViewerState? = null,
    val rescanning: Boolean = false,
) {
    val openBoard: VaultBoardSection? get() = boards.firstOrNull { it.key == selection.board }
    val openThread: VaultThreadSection?
        get() = openBoard?.threads?.firstOrNull { it.location.sameThreadAs(selection.thread) }

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

    private val rescanning = MutableStateFlow(false)

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


    val uiState: StateFlow<VaultUiState> = combine(
        mediaVault.entries(), rescanning, selection, viewingUrl, shuffleOrder,
    ) { entries, busy, sel, url, shuffle ->
        VaultUiState(
            entries = entries,
            boards = groupByBoard(entries),
            selection = sel,
            viewer = viewerState(entries, sel, url, shuffle),
            rescanning = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

    fun openBoard(key: VaultBoardKey) = selection.update { VaultSelection(board = key) }

    fun openThread(location: VaultLocation) = selection.update { it.copy(thread = location) }

    /** One step up the drill-down: thread → board → root. */
    fun navigateUp() = selection.update {
        if (it.thread != null) it.copy(thread = null) else VaultSelection()
    }

    fun openViewer(url: String) { viewingUrl.value = url }

    /** Called as the viewer pages, so rotation reopens it in place. */
    fun onViewerPage(url: String) { viewingUrl.value = url }

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

    fun hasStorageAccess(): Boolean = mediaVault.hasStorageAccess()

    fun rescan() {
        if (rescanning.value) return
        viewModelScope.launch {
            rescanning.value = true
            mediaVault.migrateLegacyIfNeeded()
            mediaVault.rescan()
            rescanning.value = false
        }
    }

    suspend fun delete(url: String): VaultError? = mediaVault.delete(url)

    private fun groupByBoard(entries: List<VaultEntry>): List<VaultBoardSection> =
        entries
            .groupBy { boardKeyOf(it.location) }
            .toList()
            .sortedBy { (key, _) -> sortKeyOf(key) }
            .map { (key, group) ->
                val threads = group
                    .groupBy { (it.location as? VaultLocation.Thread)?.threadNo ?: 0L }
                    .map { (_, threadEntries) ->
                        VaultThreadSection(threadEntries.first().location, threadEntries)
                    }
                    .sortedByDescending { (it.location as? VaultLocation.Thread)?.threadNo ?: 0L }
                VaultBoardSection(key = key, threads = threads, entries = group)
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
            ?: entries.filter { it.location.sameThreadAs(sel.thread) }.ifEmpty { listOf(current) }
        val index = ordered.indexOfFirst { it.url == current.url }.coerceAtLeast(0)
        return VaultViewerState(ordered, index)
    }

    private fun boardKeyOf(location: VaultLocation): VaultBoardKey = when (location) {
        is VaultLocation.Thread -> VaultBoardKey.Board(location.board)
        VaultLocation.Unsorted -> VaultBoardKey.Unsorted
    }

    /** Preserves the old explorer order: `_unsorted` sorts ahead of board codes. */
    private fun sortKeyOf(key: VaultBoardKey): String = when (key) {
        is VaultBoardKey.Board -> key.code
        VaultBoardKey.Unsorted -> "_unsorted"
    }
}

/** Same thread regardless of subject drift between rows. */
internal fun VaultLocation.sameThreadAs(other: VaultLocation?): Boolean = when {
    this is VaultLocation.Thread && other is VaultLocation.Thread ->
        board == other.board && threadNo == other.threadNo
    else -> this is VaultLocation.Unsorted && other is VaultLocation.Unsorted
}

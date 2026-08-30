package dev.stan.yotsuba.feature.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BookmarkSortOrder { UNREAD_FIRST, LAST_ACTIVITY, BOOKMARKED }

/** Outcome of [BookmarksViewModel.snapshot], held until the UI has shown it. */
sealed interface SnapshotResult {
    data object Saved : SnapshotResult
    data class Failed(val error: VaultError) : SnapshotResult
}

data class BookmarksUiState(
    val bookmarks: List<Bookmark> = emptyList(),
    /** Non-null while a refresh pass is running: boards done / boards total. */
    val checking: Pair<Int, Int>? = null,
    val sortOrder: BookmarkSortOrder = BookmarkSortOrder.UNREAD_FIRST,
    val loaded: Boolean = false,
    /** Keys (see [snapshotKey]) whose vault snapshot is still being written. */
    val snapshotting: Set<String> = emptySet(),
) {
    val hasDead: Boolean get() = bookmarks.any { it.isDead }

    fun isSnapshotting(bookmark: Bookmark): Boolean =
        snapshotKey(bookmark.board, bookmark.threadNo) in snapshotting
}

private fun snapshotKey(board: String, threadNo: Long) = "$board/$threadNo"

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val repository: BookmarkRepository,
    private val vault: MediaVaultRepository,
) : ViewModel() {

    private val checking = MutableStateFlow<Pair<Int, Int>?>(null)
    private val sortOrder = MutableStateFlow(BookmarkSortOrder.UNREAD_FIRST)
    private val snapshotting = MutableStateFlow<Set<String>>(emptySet())
    private val pendingSnapshotResult = MutableStateFlow<SnapshotResult?>(null)

    /**
     * The last finished [snapshot] nobody has shown yet. It stays put while the list is off
     * screen, so a snackbar can still be shown when the user comes back; the UI clears it with
     * [onSnapshotResultShown]. Two snapshots finishing while nobody looks surface as one.
     */
    val snapshotResult: StateFlow<SnapshotResult?> = pendingSnapshotResult
    private var refreshJob: Job? = null
    private var lastAutoRefreshAt = 0L

    val uiState: StateFlow<BookmarksUiState> = combine(
        repository.bookmarks, checking, sortOrder, snapshotting,
    ) { bookmarks, progress, order, snapping ->
        BookmarksUiState(
            bookmarks = sort(bookmarks, order),
            checking = progress,
            sortOrder = order,
            loaded = true,
            snapshotting = snapping,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookmarksUiState())

    /**
     * Fired every time the screen comes on screen: refreshes automatically, but at most
     * once a minute so tab-hopping doesn't hammer the API (each board costs a >=1 s request).
     */
    fun onScreenVisible() {
        val now = System.currentTimeMillis()
        if (now - lastAutoRefreshAt < 60_000) return
        lastAutoRefreshAt = now
        onRefreshAll()
    }

    /** Board-grouped pass; the spinner stays up until the repository returns. */
    fun onRefreshAll() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            checking.value = 0 to 0
            try {
                repository.refreshAll { done, total -> checking.value = done to total }
            } finally {
                checking.value = null
            }
        }
    }

    fun onSortOrderChanged(order: BookmarkSortOrder) {
        sortOrder.value = order
    }

    fun onTogglePinned(bookmark: Bookmark) = viewModelScope.launch {
        repository.setPinned(bookmark.board, bookmark.threadNo, !bookmark.pinned)
    }

    fun onRemoveDead() = viewModelScope.launch { repository.removeDead() }

    /**
     * Writes the thread's posts into the vault as a sidecar (no media), so the text outlives
     * the thread. A second tap on a row that's already snapshotting is ignored.
     */
    fun snapshot(board: String, threadNo: Long) {
        val key = snapshotKey(board, threadNo)
        if (key in snapshotting.value) return
        viewModelScope.launch {
            snapshotting.value = snapshotting.value + key
            try {
                val error = vault.snapshotThread(board, threadNo)
                pendingSnapshotResult.value =
                    if (error == null) SnapshotResult.Saved else SnapshotResult.Failed(error)
            } finally {
                snapshotting.value = snapshotting.value - key
            }
        }
    }

    fun onSnapshotResultShown() {
        pendingSnapshotResult.value = null
    }

    fun onRemove(bookmark: Bookmark) = viewModelScope.launch {
        repository.remove(bookmark.board, bookmark.threadNo)
    }

    fun onUndoRemove(bookmark: Bookmark) = viewModelScope.launch {
        repository.add(bookmark)
    }

    override fun onCleared() {
        refreshJob?.cancel()
    }

    companion object {
        /** Pinned rows always lead; within each group the chosen order applies. */
        private fun sort(list: List<Bookmark>, order: BookmarkSortOrder): List<Bookmark> {
            val activity = { b: Bookmark -> b.lastActivityAt ?: b.bookmarkedAt }
            val comparator: Comparator<Bookmark> = when (order) {
                BookmarkSortOrder.UNREAD_FIRST ->
                    compareByDescending<Bookmark> { it.unread }.thenByDescending(activity)
                BookmarkSortOrder.LAST_ACTIVITY -> compareByDescending(activity)
                BookmarkSortOrder.BOOKMARKED -> compareByDescending { it.bookmarkedAt }
            }
            return list.sortedWith(compareByDescending<Bookmark> { it.pinned }.then(comparator))
        }
    }
}

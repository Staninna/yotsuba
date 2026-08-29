package dev.stan.yotsuba.feature.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BookmarkSortOrder { UNREAD_FIRST, LAST_ACTIVITY, BOOKMARKED }

data class BookmarksUiState(
    val bookmarks: List<Bookmark> = emptyList(),
    /** Non-null while a refresh pass is running: boards done / boards total. */
    val checking: Pair<Int, Int>? = null,
    val sortOrder: BookmarkSortOrder = BookmarkSortOrder.UNREAD_FIRST,
    val loaded: Boolean = false,
) {
    val hasDead: Boolean get() = bookmarks.any { it.isDead }
}

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val repository: BookmarkRepository,
) : ViewModel() {

    private val checking = MutableStateFlow<Pair<Int, Int>?>(null)
    private val sortOrder = MutableStateFlow(BookmarkSortOrder.UNREAD_FIRST)
    private var refreshJob: Job? = null
    private var lastAutoRefreshAt = 0L

    val uiState: StateFlow<BookmarksUiState> = combine(
        repository.bookmarks, checking, sortOrder,
    ) { bookmarks, progress, order ->
        BookmarksUiState(
            bookmarks = sort(bookmarks, order),
            checking = progress,
            sortOrder = order,
            loaded = true,
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

    fun onRemove(bookmark: Bookmark) = viewModelScope.launch {
        repository.remove(bookmark.board, bookmark.threadNo)
    }

    fun onUndoRemove(bookmark: Bookmark) = viewModelScope.launch {
        repository.add(bookmark)
    }

    override fun onCleared() {
        refreshJob?.cancel()
    }

    private companion object {
        /** Pinned rows always lead; within each group the chosen order applies. */
        fun sort(list: List<Bookmark>, order: BookmarkSortOrder): List<Bookmark> {
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

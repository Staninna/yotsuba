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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookmarksUiState(
    val bookmarks: List<Bookmark> = emptyList(),
    /** Non-null while refresh-all is walking: current / total (D9). */
    val checking: Pair<Int, Int>? = null,
    val loaded: Boolean = false,
)

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val repository: BookmarkRepository,
) : ViewModel() {

    private val checking = MutableStateFlow<Pair<Int, Int>?>(null)
    private var refreshJob: Job? = null
    private var lastAutoRefreshAt = 0L

    val uiState: StateFlow<BookmarksUiState> = combine(
        repository.bookmarks, checking,
    ) { bookmarks, checkingProgress ->
        BookmarksUiState(bookmarks = bookmarks, checking = checkingProgress, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookmarksUiState())

    /**
     * Sequential walk (the >=1 s rate limit forbids fanning out); each row updates as its
     * response lands. Scoped to the ViewModel: leaving cancels, checked rows keep state (D9).
     */
    /**
     * Fired every time the screen comes on screen: refreshes automatically, but at most
     * once a minute so tab-hopping doesn't hammer the API (each row costs a >=1 s request).
     */
    fun onScreenVisible() {
        val now = System.currentTimeMillis()
        if (now - lastAutoRefreshAt < 60_000) return
        lastAutoRefreshAt = now
        onRefreshAll()
    }

    fun onRefreshAll() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val list = repository.bookmarks.first()
            list.forEachIndexed { index, bookmark ->
                checking.value = (index + 1) to list.size
                repository.refreshOne(bookmark)
            }
            checking.value = null
        }
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
}

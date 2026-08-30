package dev.stan.yotsuba.fake

import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.repository.BookmarkRefreshSummary
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Bookmarks in a state flow, plus counters for what a screen asked of it. [bookmarkedFlow]
 * is the watched flag of the one thread under test: [add]/[remove] flip it, and a test may
 * set it directly without building a [Bookmark].
 */
class FakeBookmarkRepository(initial: List<Bookmark> = emptyList()) : BookmarkRepository {
    val state = MutableStateFlow(initial)
    val bookmarkedFlow = MutableStateFlow(false)
    var added: Bookmark? = null
    var removedCount = 0
    var cleared = false
    var refreshAllCalls = 0
    var removeDeadCalls = 0
    /** Holds [refreshAll] between its two progress reports while set. */
    var gate: CompletableDeferred<Unit>? = null
    /** Every markSeen call, in order; the repository itself never lowers the mark. */
    val seen = mutableListOf<Long>()

    override val bookmarks: Flow<List<Bookmark>> get() = state
    override suspend fun add(bookmark: Bookmark) {
        added = bookmark
        bookmarkedFlow.value = true
        state.value = state.value + bookmark
    }
    override suspend fun remove(board: String, threadNo: Long) {
        removedCount++
        bookmarkedFlow.value = false
        state.value = state.value.filterNot { it.board == board && it.threadNo == threadNo }
    }
    override fun isBookmarked(board: String, threadNo: Long): Flow<Boolean> =
        combine(bookmarkedFlow, state) { flag, list -> flag || list.any { it.board == board && it.threadNo == threadNo } }
    override suspend fun refreshAll(onProgress: (Int, Int) -> Unit): BookmarkRefreshSummary {
        refreshAllCalls++
        onProgress(0, 2)
        gate?.await()
        onProgress(2, 2)
        return BookmarkRefreshSummary()
    }
    override suspend fun markSeen(board: String, threadNo: Long, postNo: Long) { seen += postNo }
    override suspend fun setPinned(board: String, threadNo: Long, pinned: Boolean) {
        state.value = state.value.map { if (it.board == board && it.threadNo == threadNo) it.copy(pinned = pinned) else it }
    }
    override suspend fun removeDead() { removeDeadCalls++ }
    override suspend fun clearAll() {
        cleared = true
        state.value = emptyList()
    }
}

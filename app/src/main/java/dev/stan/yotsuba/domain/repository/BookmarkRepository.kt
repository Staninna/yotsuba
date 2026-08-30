package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

/** What one refresh pass changed, for the "N new replies in M threads" notification. */
data class BookmarkRefreshSummary(
    val threadsChecked: Int = 0,
    /** Unread that appeared during this pass, summed over threads. */
    val newUnread: Int = 0,
    /** Threads whose unread count grew. */
    val threadsWithNew: Int = 0,
)

interface BookmarkRepository {
    val bookmarks: Flow<List<Bookmark>>
    suspend fun add(bookmark: Bookmark)
    suspend fun remove(board: String, threadNo: Long)
    fun isBookmarked(board: String, threadNo: Long): Flow<Boolean>

    /**
     * One catalog call per board, thread JSON only where the reply count moved; archived and
     * pruned rows are skipped. [onProgress] reports `(boardsDone, boardsTotal)`.
     */
    suspend fun refreshAll(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): BookmarkRefreshSummary

    /**
     * The user has had [lastSeenPostNo] on screen: raise the read mark to it. Never lowers it
     * and touches nothing else.
     */
    suspend fun markSeen(board: String, threadNo: Long, lastSeenPostNo: Long)

    suspend fun setPinned(board: String, threadNo: Long, pinned: Boolean)

    /** Drops every pruned (DEAD) bookmark; archived ones stay readable and stay. */
    suspend fun removeDead()
    suspend fun clearAll()
}

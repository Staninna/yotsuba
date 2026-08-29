package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
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

    /** One thread, full JSON: the pull-on-one-row path. */
    suspend fun refreshOne(bookmark: Bookmark): Bookmark

    /**
     * One catalog call per board, thread JSON only where the reply count moved; archived and
     * pruned rows are skipped. [onProgress] reports `(boardsDone, boardsTotal)`.
     */
    suspend fun refreshAll(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): BookmarkRefreshSummary =
        BookmarkRefreshSummary()

    /**
     * The user has had [lastSeenPostNo] on screen: raise the read mark to it. Never lowers it
     * and touches nothing else. [replyCount] is ignored; it stays for source compatibility.
     */
    suspend fun markSeen(board: String, threadNo: Long, lastSeenPostNo: Long, replyCount: Int)

    /** No-op: unread is derived from the read mark. Call markSeen with the bottom-most visible post instead. */
    @Deprecated("Unread is derived from readUpTo; use markSeen")
    suspend fun updateUnread(board: String, threadNo: Long, unread: Int)

    suspend fun setPinned(board: String, threadNo: Long, pinned: Boolean) {}

    /** Drops every pruned (DEAD) bookmark; archived ones stay readable and stay. */
    suspend fun removeDead() {}
    suspend fun clearAll()
}

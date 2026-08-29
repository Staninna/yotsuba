package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import kotlinx.coroutines.flow.Flow

interface BoardRepository {
    /** The single place `/f/` is excluded (D13). */
    suspend fun boards(forceRefresh: Boolean = false): DataResult<List<Board>>
    suspend fun board(code: String): Board?
}

interface CatalogRepository {
    suspend fun catalog(board: String, forceRefresh: Boolean = false): DataResult<List<CatalogThread>>
}

interface ThreadRepository {
    suspend fun thread(board: String, no: Long, forceRefresh: Boolean = false): DataResult<ThreadDetails>
}

interface BookmarkRepository {
    val bookmarks: Flow<List<Bookmark>>
    suspend fun add(bookmark: Bookmark)
    suspend fun remove(board: String, threadNo: Long)
    fun isBookmarked(board: String, threadNo: Long): Flow<Boolean>
    suspend fun refreshOne(bookmark: Bookmark): Bookmark

    /** The user is looking at the thread: record the newest post and zero the unread count. */
    suspend fun markSeen(board: String, threadNo: Long, lastSeenPostNo: Long, replyCount: Int)

    /** Live update from the open thread: how many loaded posts are still below the read mark. */
    suspend fun updateUnread(board: String, threadNo: Long, unread: Int)
    suspend fun clearAll()
}

interface HiddenThreadsRepository {
    val all: Flow<List<HiddenThread>>
    fun forBoard(board: String): Flow<List<HiddenThread>>
    suspend fun hide(board: String, threadNo: Long)
    suspend fun unhide(board: String, threadNo: Long)
}

interface MaintenanceRepository {
    /** Evicts the OkHttp API cache and deletes the Coil image cache directory. */
    suspend fun clearCaches()
}

interface HistoryRepository {
    val history: Flow<List<HistoryEntry>>
    suspend fun record(entry: HistoryEntry)
    suspend fun updateScrollPosition(board: String, threadNo: Long, postNo: Long)
    suspend fun lastScrollPosition(board: String, threadNo: Long): Long?

    /** Raises the read high-water mark (bottom-most post that has been on screen). */
    suspend fun updateReadUpTo(board: String, threadNo: Long, postNo: Long)
    suspend fun readUpTo(board: String, threadNo: Long): Long?
    suspend fun remove(board: String, threadNo: Long)
    suspend fun clearAll()
    suspend fun trim(retainAfterMs: Long)
}

interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun update(transform: (Settings) -> Settings)
}

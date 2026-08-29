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

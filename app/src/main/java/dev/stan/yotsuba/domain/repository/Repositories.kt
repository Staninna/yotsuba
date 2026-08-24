package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.CatalogThread
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
    suspend fun isBookmarked(board: String, threadNo: Long): Flow<Boolean>
    suspend fun refreshOne(bookmark: Bookmark): Bookmark

    /** The user is looking at the thread: record the newest post and zero the unread count. */
    suspend fun markSeen(board: String, threadNo: Long, lastSeenPostNo: Long, replyCount: Int)

    /** Live update from the open thread: how many loaded posts are still below the read mark. */
    suspend fun updateUnread(board: String, threadNo: Long, unread: Int)
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

/** Everything known about a post at save time, used to file media into the vault. */
data class VaultSaveContext(
    val board: String,
    val threadNo: Long,
    /** Thread (OP) subject, for the thread directory slug. */
    val threadSubject: String?,
    /** Plain-text OP excerpt, slug fallback when the thread has no subject. */
    val opExcerpt: String?,
    val post: dev.stan.yotsuba.domain.model.ThreadPost?,
)

interface MediaVaultRepository {
    /** True when the app may read/write the vault directory. */
    fun hasStorageAccess(): Boolean

    /** Streams the media to its structured vault location, updates meta.json and the DB. */
    suspend fun save(item: dev.stan.yotsuba.domain.model.MediaItem, context: VaultSaveContext): Boolean

    /** Deletes the file, its meta entry, and the DB row; prunes emptied thread directories. */
    suspend fun delete(url: String): Boolean

    /** Rebuilds the saved-media DB purely from the meta.json sidecars on disk. */
    suspend fun rescan()

    /** One-time move of the legacy flat Pictures/Yotsuba files into the vault. No-op once done. */
    suspend fun migrateLegacyIfNeeded()
}

interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun update(transform: (Settings) -> Settings)
}

package dev.stan.yotsuba.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.stan.yotsuba.core.database.entity.BookmarkEntity
import dev.stan.yotsuba.core.database.entity.DownloadedMediaEntity
import dev.stan.yotsuba.core.database.entity.HiddenThreadEntity
import dev.stan.yotsuba.core.database.entity.HistoryEntity
import dev.stan.yotsuba.core.database.entity.SavedMediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY bookmarkedAt DESC")
    fun all(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE board = :board AND threadNo = :threadNo")
    suspend fun delete(board: String, threadNo: Long)

    @Query("SELECT COUNT(*) > 0 FROM bookmarks WHERE board = :board AND threadNo = :threadNo")
    fun isBookmarked(board: String, threadNo: Long): Flow<Boolean>

    @Query(
        "UPDATE bookmarks SET lastSeenPostNo = :lastSeenPostNo, newReplies = 0, " +
            "replyCount = :replyCount WHERE board = :board AND threadNo = :threadNo"
    )
    suspend fun markSeen(board: String, threadNo: Long, lastSeenPostNo: Long, replyCount: Int)

    /** Refresh writes only the columns it owns; concurrent markSeen/delete are never clobbered. */
    @Query(
        "UPDATE bookmarks SET replyCount = :replyCount, imageCount = :imageCount, state = :state, " +
            "lastCheckedAt = :lastCheckedAt, newReplies = :newReplies, unreadCount = :unreadCount " +
            "WHERE board = :board AND threadNo = :threadNo"
    )
    suspend fun updateRefresh(
        board: String,
        threadNo: Long,
        replyCount: Int,
        imageCount: Int,
        state: String,
        lastCheckedAt: Long?,
        newReplies: Int,
        unreadCount: Int,
    )

    @Query("UPDATE bookmarks SET unreadCount = :unread WHERE board = :board AND threadNo = :threadNo")
    suspend fun updateUnread(board: String, threadNo: Long, unread: Int)

    @Query("DELETE FROM bookmarks")
    suspend fun clearAll()
}

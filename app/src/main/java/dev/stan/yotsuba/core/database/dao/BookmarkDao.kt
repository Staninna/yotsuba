package dev.stan.yotsuba.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.stan.yotsuba.core.database.entity.BookmarkEntity
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

    /**
     * The one write to the read mark; it only ever rises, so a stale caller can't push it
     * back. Nothing else is touched, and a call that would not raise it writes no row, so
     * it fires no invalidation either.
     */
    @Query(
        "UPDATE bookmarks SET readUpTo = MAX(COALESCE(readUpTo, 0), :postNo) " +
            "WHERE board = :board AND threadNo = :threadNo AND (readUpTo IS NULL OR readUpTo < :postNo)"
    )
    suspend fun markSeen(board: String, threadNo: Long, postNo: Long)

    /**
     * Refresh writes only the columns it owns (counts, state, activity, post list). readUpTo
     * and pinned are never in this statement, so a concurrent markSeen/setPinned survives.
     */
    @Query(
        "UPDATE bookmarks SET replyCount = :replyCount, imageCount = :imageCount, state = :state, " +
            "lastCheckedAt = :lastCheckedAt, lastActivityAt = :lastActivityAt, " +
            "postNos = :postNos WHERE board = :board AND threadNo = :threadNo"
    )
    suspend fun updateRefresh(
        board: String,
        threadNo: Long,
        replyCount: Int,
        imageCount: Int,
        state: String,
        lastCheckedAt: Long?,
        lastActivityAt: Long?,
        postNos: String,
    )

    /** Catalog-only refresh: counts and activity moved, but the post list wasn't fetched. */
    @Query(
        "UPDATE bookmarks SET replyCount = :replyCount, imageCount = :imageCount, state = :state, " +
            "lastCheckedAt = :lastCheckedAt, lastActivityAt = :lastActivityAt " +
            "WHERE board = :board AND threadNo = :threadNo"
    )
    suspend fun updateCounts(
        board: String,
        threadNo: Long,
        replyCount: Int,
        imageCount: Int,
        state: String,
        lastCheckedAt: Long?,
        lastActivityAt: Long?,
    )

    @Query("UPDATE bookmarks SET state = :state, lastCheckedAt = :lastCheckedAt WHERE board = :board AND threadNo = :threadNo")
    suspend fun updateState(board: String, threadNo: Long, state: String, lastCheckedAt: Long?)

    @Query("UPDATE bookmarks SET pinned = :pinned WHERE board = :board AND threadNo = :threadNo")
    suspend fun setPinned(board: String, threadNo: Long, pinned: Boolean)

    @Query("DELETE FROM bookmarks WHERE state = 'DEAD'")
    suspend fun deleteDead()

    @Query("DELETE FROM bookmarks")
    suspend fun clearAll()
}

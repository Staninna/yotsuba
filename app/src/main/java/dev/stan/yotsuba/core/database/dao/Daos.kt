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

    /**
     * The one write to the read mark; it only ever rises, so a stale caller can't push it
     * back. Nothing else is touched.
     */
    @Query(
        "UPDATE bookmarks SET readUpTo = MAX(COALESCE(readUpTo, 0), :postNo) " +
            "WHERE board = :board AND threadNo = :threadNo"
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

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY viewedAt DESC")
    fun all(): Flow<List<HistoryEntity>>

    @Query(
        "UPDATE history SET subject = :subject, opExcerpt = :opExcerpt, " +
            "thumbnailUrl = :thumbnailUrl, viewedAt = :viewedAt " +
            "WHERE board = :board AND threadNo = :threadNo"
    )
    suspend fun updateVisit(
        board: String,
        threadNo: Long,
        subject: String?,
        opExcerpt: String,
        thumbnailUrl: String?,
        viewedAt: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: HistoryEntity)

    /**
     * Upsert of the visit columns only — scroll and read marks are owned by
     * updateScroll/updateMaxRead and survive revisits.
     */
    @Transaction
    suspend fun record(entity: HistoryEntity) {
        val updated = updateVisit(
            entity.board, entity.threadNo, entity.subject,
            entity.opExcerpt, entity.thumbnailUrl, entity.viewedAt,
        )
        if (updated == 0) insertIgnore(entity)
    }

    @Query("SELECT lastScrollPostNo FROM history WHERE board = :board AND threadNo = :threadNo")
    suspend fun lastScroll(board: String, threadNo: Long): Long?

    @Query("UPDATE history SET lastScrollPostNo = :postNo WHERE board = :board AND threadNo = :threadNo")
    suspend fun updateScroll(board: String, threadNo: Long, postNo: Long)

    @Query(
        "UPDATE history SET maxReadPostNo = :postNo WHERE board = :board AND threadNo = :threadNo " +
            "AND (maxReadPostNo IS NULL OR maxReadPostNo < :postNo)"
    )
    suspend fun updateMaxRead(board: String, threadNo: Long, postNo: Long)

    @Query("SELECT maxReadPostNo FROM history WHERE board = :board AND threadNo = :threadNo")
    suspend fun maxRead(board: String, threadNo: Long): Long?

    @Query("DELETE FROM history WHERE board = :board AND threadNo = :threadNo")
    suspend fun delete(board: String, threadNo: Long)

    @Query("DELETE FROM history")
    suspend fun clearAll()

    @Query("DELETE FROM history WHERE viewedAt < :cutoffMs")
    suspend fun trimOlderThan(cutoffMs: Long)
}

@Dao
interface HiddenThreadDao {
    @Query("SELECT * FROM hidden_threads")
    fun all(): Flow<List<HiddenThreadEntity>>

    @Query("SELECT * FROM hidden_threads WHERE board = :board")
    fun forBoard(board: String): Flow<List<HiddenThreadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hide(entity: HiddenThreadEntity)

    @Query("DELETE FROM hidden_threads WHERE board = :board AND threadNo = :threadNo")
    suspend fun unhide(board: String, threadNo: Long)
}

/** Legacy table superseded by [SavedMediaDao]; read-only so nothing can write it anymore. */
@Dao
interface DownloadedMediaDao {
    @Query("SELECT * FROM downloaded_media")
    suspend fun allOnce(): List<DownloadedMediaEntity>
}

@Dao
interface SavedMediaDao {
    @Query("SELECT url FROM saved_media")
    fun urls(): Flow<List<String>>

    @Query("SELECT * FROM saved_media ORDER BY savedAt DESC")
    fun all(): Flow<List<SavedMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SavedMediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SavedMediaEntity>)

    @Query("DELETE FROM saved_media WHERE url = :url")
    suspend fun delete(url: String)

    @Query("SELECT * FROM saved_media WHERE url = :url")
    suspend fun byUrl(url: String): SavedMediaEntity?

    @Query("DELETE FROM saved_media")
    suspend fun clearAll()

    /** Atomic rebuild for rescan — a crash mid-rescan can never leave the index empty. */
    @Transaction
    suspend fun replaceAll(entities: List<SavedMediaEntity>) {
        clearAll()
        insertAll(entities)
    }
}

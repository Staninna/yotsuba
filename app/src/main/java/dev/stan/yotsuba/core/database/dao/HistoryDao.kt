package dev.stan.yotsuba.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.stan.yotsuba.core.database.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

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

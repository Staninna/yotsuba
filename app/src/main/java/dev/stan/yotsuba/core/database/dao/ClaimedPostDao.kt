package dev.stan.yotsuba.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.stan.yotsuba.core.database.entity.ClaimedPostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClaimedPostDao {
    @Query("SELECT postNo FROM claimed_posts WHERE board = :board AND threadNo = :threadNo")
    fun forThread(board: String, threadNo: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun claim(entity: ClaimedPostEntity)

    @Query("DELETE FROM claimed_posts WHERE board = :board AND threadNo = :threadNo AND postNo = :postNo")
    suspend fun unclaim(board: String, threadNo: Long, postNo: Long)
}

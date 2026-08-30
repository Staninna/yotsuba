package dev.stan.yotsuba.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.stan.yotsuba.core.database.entity.HiddenThreadEntity
import kotlinx.coroutines.flow.Flow

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

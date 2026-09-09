package dev.stan.yotsuba.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.stan.yotsuba.core.database.entity.UsageEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageEventDao {
    @Insert
    suspend fun insert(event: UsageEventEntity)

    @Query("SELECT * FROM usage_events ORDER BY at")
    fun all(): Flow<List<UsageEventEntity>>
}

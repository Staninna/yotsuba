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

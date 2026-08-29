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

    @Query("SELECT * FROM saved_media")
    suspend fun allOnce(): List<SavedMediaEntity>

    @Query("SELECT * FROM saved_media WHERE md5 = :md5 AND absolutePath != '' LIMIT 1")
    suspend fun byMd5(md5: String): SavedMediaEntity?

    /** Rows the hasher still has to visit: no MD5, or an image with no dHash. */
    @Query(
        "SELECT * FROM saved_media WHERE absolutePath != '' AND " +
            "(md5 IS NULL OR (phash IS NULL AND ext NOT IN ('.webm', '.mp4')))",
    )
    suspend fun missingHashes(): List<SavedMediaEntity>

    @Query("UPDATE saved_media SET md5 = :md5 WHERE url = :url")
    suspend fun updateMd5(url: String, md5: String)

    @Query("UPDATE saved_media SET md5 = :md5, phash = :phash, pixelSize = :pixelSize WHERE url = :url")
    suspend fun updateHashes(url: String, md5: String, phash: Long?, pixelSize: Long?)

    /** Atomic rebuild for rescan — a crash mid-rescan can never leave the index empty. */
    @Transaction
    suspend fun replaceAll(entities: List<SavedMediaEntity>) {
        clearAll()
        insertAll(entities)
    }
}

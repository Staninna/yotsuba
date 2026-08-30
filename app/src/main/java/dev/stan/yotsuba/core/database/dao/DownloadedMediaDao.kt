package dev.stan.yotsuba.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import dev.stan.yotsuba.core.database.entity.DownloadedMediaEntity

/** Legacy table superseded by [SavedMediaDao]; read-only so nothing can write it anymore. */
@Dao
interface DownloadedMediaDao {
    @Query("SELECT * FROM downloaded_media")
    suspend fun allOnce(): List<DownloadedMediaEntity>
}

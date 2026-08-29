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

/** Legacy table superseded by [SavedMediaDao]; read-only so nothing can write it anymore. */
@Dao
interface DownloadedMediaDao {
    @Query("SELECT * FROM downloaded_media")
    suspend fun allOnce(): List<DownloadedMediaEntity>
}

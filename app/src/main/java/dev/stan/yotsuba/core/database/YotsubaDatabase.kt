package dev.stan.yotsuba.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.stan.yotsuba.core.database.dao.BookmarkDao
import dev.stan.yotsuba.core.database.dao.DownloadedMediaDao
import dev.stan.yotsuba.core.database.dao.HiddenThreadDao
import dev.stan.yotsuba.core.database.dao.HistoryDao
import dev.stan.yotsuba.core.database.dao.SavedMediaDao
import dev.stan.yotsuba.core.database.entity.BookmarkEntity
import dev.stan.yotsuba.core.database.entity.DownloadedMediaEntity
import dev.stan.yotsuba.core.database.entity.HiddenThreadEntity
import dev.stan.yotsuba.core.database.entity.HistoryEntity
import dev.stan.yotsuba.core.database.entity.SavedMediaEntity

@Database(
    entities = [
        BookmarkEntity::class, HistoryEntity::class, HiddenThreadEntity::class,
        DownloadedMediaEntity::class, SavedMediaEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class YotsubaDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun hiddenThreadDao(): HiddenThreadDao
    abstract fun downloadedMediaDao(): DownloadedMediaDao
    abstract fun savedMediaDao(): SavedMediaDao
}

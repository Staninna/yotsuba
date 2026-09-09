package dev.stan.yotsuba.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.stan.yotsuba.core.database.dao.BookmarkDao
import dev.stan.yotsuba.core.database.dao.ClaimedPostDao
import dev.stan.yotsuba.core.database.dao.DownloadedMediaDao
import dev.stan.yotsuba.core.database.dao.HiddenThreadDao
import dev.stan.yotsuba.core.database.dao.HistoryDao
import dev.stan.yotsuba.core.database.dao.SavedMediaDao
import dev.stan.yotsuba.core.database.dao.UsageEventDao
import dev.stan.yotsuba.core.database.entity.BookmarkEntity
import dev.stan.yotsuba.core.database.entity.ClaimedPostEntity
import dev.stan.yotsuba.core.database.entity.DownloadedMediaEntity
import dev.stan.yotsuba.core.database.entity.HiddenThreadEntity
import dev.stan.yotsuba.core.database.entity.HistoryEntity
import dev.stan.yotsuba.core.database.entity.SavedMediaEntity
import dev.stan.yotsuba.core.database.entity.UsageEventEntity

@Database(
    entities = [
        BookmarkEntity::class, HistoryEntity::class, HiddenThreadEntity::class,
        DownloadedMediaEntity::class, SavedMediaEntity::class, ClaimedPostEntity::class,
        UsageEventEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
abstract class YotsubaDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun hiddenThreadDao(): HiddenThreadDao
    abstract fun downloadedMediaDao(): DownloadedMediaDao
    abstract fun savedMediaDao(): SavedMediaDao
    abstract fun claimedPostDao(): ClaimedPostDao
    abstract fun usageEventDao(): UsageEventDao
}

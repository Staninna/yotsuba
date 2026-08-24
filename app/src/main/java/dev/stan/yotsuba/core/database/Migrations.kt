package dev.stan.yotsuba.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `downloaded_media` " +
                "(`url` TEXT NOT NULL, `downloadedAt` INTEGER NOT NULL, PRIMARY KEY(`url`))",
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `newReplies` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_media` (" +
                "`url` TEXT NOT NULL, `board` TEXT, `threadNo` INTEGER, `postNo` INTEGER, " +
                "`subject` TEXT, `displayName` TEXT NOT NULL, `absolutePath` TEXT NOT NULL, " +
                "`ext` TEXT, `sizeBytes` INTEGER, `width` INTEGER, `height` INTEGER, " +
                "`thumbnailUrl` TEXT, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`url`))",
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `unreadCount` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `history` ADD COLUMN `maxReadPostNo` INTEGER")
    }
}

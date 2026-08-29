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

/** One read mark (readUpTo, seeded from the old last-seen marker), pin flag, activity time. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `readUpTo` INTEGER")
        db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `postNos` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `lastActivityAt` INTEGER")
        db.execSQL("UPDATE `bookmarks` SET `readUpTo` = `lastSeenPostNo`")
    }
}

/** Local video stills and durations on saved media; a rescan fills them in. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `saved_media` ADD COLUMN `thumbnailPath` TEXT")
        db.execSQL("ALTER TABLE `saved_media` ADD COLUMN `durationMs` INTEGER")
    }
}

/** Posts the user marked as theirs, for "(You)" labels without ever posting. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `claimed_posts` (" +
                "`board` TEXT NOT NULL, `threadNo` INTEGER NOT NULL, `postNo` INTEGER NOT NULL, " +
                "`claimedAt` INTEGER NOT NULL, PRIMARY KEY(`board`, `threadNo`, `postNo`))",
        )
    }
}

/** Content hashes on saved media, for duplicate detection; a backfill fills them in. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `saved_media` ADD COLUMN `md5` TEXT")
        db.execSQL("ALTER TABLE `saved_media` ADD COLUMN `phash` INTEGER")
        db.execSQL("ALTER TABLE `saved_media` ADD COLUMN `pixelSize` INTEGER")
    }
}

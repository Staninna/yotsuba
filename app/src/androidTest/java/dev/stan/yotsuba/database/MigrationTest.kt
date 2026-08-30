package dev.stan.yotsuba.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.stan.yotsuba.core.database.MIGRATION_10_11
import dev.stan.yotsuba.core.database.MIGRATION_11_12
import dev.stan.yotsuba.core.database.YotsubaDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), YotsubaDatabase::class.java)

    @Test
    fun migrate10To11_keepsBookmarks_andIndexesMd5() {
        helper.createDatabase(dbName, 10).use { db ->
            db.execSQL(
                "INSERT INTO bookmarks (board, threadNo, subject, opExcerpt, thumbnailUrl, replyCount, imageCount, " +
                    "bookmarkedAt, lastCheckedAt, lastSeenPostNo, state, newReplies, unreadCount, readUpTo, postNos, " +
                    "pinned, lastActivityAt) VALUES ('g', 1, 's', 'e', NULL, 3, 1, 10, NULL, 5, 'ALIVE', 2, 2, 7, " +
                    "'8,9', 1, 20)",
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 11, true, MIGRATION_10_11)

        db.query("SELECT board, threadNo, readUpTo, postNos, pinned, lastActivityAt FROM bookmarks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("g", c.getString(0))
            assertEquals(1L, c.getLong(1))
            assertEquals(7L, c.getLong(2))
            assertEquals("8,9", c.getString(3))
            assertEquals(1, c.getInt(4))
            assertEquals(20L, c.getLong(5))
            assertEquals(1, c.count)
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'saved_media'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("index_saved_media_md5", c.getString(0))
        }
        db.close()
    }

    @Test
    fun migrate11To12_keepsSavedMedia_withSoundColumnsUnknown() {
        helper.createDatabase(dbName, 11).use { db ->
            db.execSQL(
                "INSERT INTO saved_media (url, board, threadNo, postNo, subject, displayName, absolutePath, ext, " +
                    "sizeBytes, width, height, thumbnailUrl, savedAt, thumbnailPath, durationMs, md5, phash, pixelSize) " +
                    "VALUES ('https://i.4cdn.org/g/1.webm', 'g', 1, 2, 's', '2_a.webm', '/sdcard/Yotsuba/g/1/2_a.webm', " +
                    "'.webm', 10, 1, 1, NULL, 5, NULL, 1500, NULL, NULL, NULL)",
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 12, true, MIGRATION_11_12)

        db.query("SELECT url, durationMs, hasAudio, soundUrl FROM saved_media").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("https://i.4cdn.org/g/1.webm", c.getString(0))
            assertEquals(1500L, c.getLong(1))
            assertTrue(c.isNull(2))
            assertTrue(c.isNull(3))
            assertEquals(1, c.count)
        }
        db.close()
    }
}

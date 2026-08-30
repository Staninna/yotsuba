package dev.stan.yotsuba.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import dev.stan.yotsuba.core.database.MIGRATION_10_11
import dev.stan.yotsuba.core.database.MIGRATION_11_12
import dev.stan.yotsuba.core.database.MIGRATION_6_7
import dev.stan.yotsuba.core.database.MIGRATION_7_8
import dev.stan.yotsuba.core.database.MIGRATION_8_9
import dev.stan.yotsuba.core.database.MIGRATION_9_10
import dev.stan.yotsuba.core.database.YotsubaDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Walks the hand-written migration chain from the oldest schema still shipped (v6) to the
 * current version on the JVM.
 *
 * room-testing is only on the androidTest classpath, so instead of MigrationTestHelper this
 * builds v6 from the exported `schemas/6.json` (the same file the helper would read), runs the
 * migrations, then opens the result through Room with no destructive fallback. Room validates
 * every table against the generated schema on open and throws if a migration left anything
 * behind, which is the check runMigrationsAndValidate performs.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationChainTest {
    private val dbName = "migration-chain-test"
    private val startVersion = 6
    private val migrations = arrayOf(
        MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
    )
    private lateinit var context: Context
    private var opened: YotsubaDatabase? = null

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After fun tearDown() {
        opened?.close()
        context.deleteDatabase(dbName)
    }

    @Test fun `every migration is registered and the chain reaches the current version`() {
        var expected = startVersion
        for (m in migrations) {
            assertEquals("migration chain has a gap before $expected", expected, m.startVersion)
            expected = m.endVersion
        }
        assertEquals(currentVersion(), expected)
    }

    @Test fun `rows written at v6 survive every migration to the current version`() = runTest {
        createV6 { db ->
            db.execSQL(
                "INSERT INTO bookmarks (board, threadNo, subject, opExcerpt, thumbnailUrl, replyCount, imageCount, " +
                    "bookmarkedAt, lastCheckedAt, lastSeenPostNo, state, newReplies, unreadCount) " +
                    "VALUES ('g', 9400000001, 'subj', 'excerpt', NULL, 3, 1, 10, NULL, 7, 'ALIVE', 2, 2)",
            )
            db.execSQL(
                "INSERT INTO history (board, threadNo, subject, opExcerpt, thumbnailUrl, viewedAt, " +
                    "lastScrollPostNo, maxReadPostNo) VALUES ('a', 2, NULL, 'hist', NULL, 100, 5, 6)",
            )
            db.execSQL("INSERT INTO hidden_threads (board, threadNo, hiddenAt) VALUES ('v', 3, 30)")
            db.execSQL("INSERT INTO downloaded_media (url, downloadedAt) VALUES ('https://i.4cdn.org/g/d.jpg', 40)")
            db.execSQL(
                "INSERT INTO saved_media (url, board, threadNo, postNo, subject, displayName, absolutePath, ext, " +
                    "sizeBytes, width, height, thumbnailUrl, savedAt) VALUES ('https://i.4cdn.org/g/1.webm', 'g', " +
                    "1, 2, 's', '2_a.webm', '/sdcard/Yotsuba/g/1/2_a.webm', '.webm', 10, 1, 1, NULL, 5)",
            )
        }

        val db = openCurrent()

        val bookmark = db.bookmarkDao().all().first().single()
        assertEquals(9_400_000_001L, bookmark.threadNo)
        assertEquals("subj", bookmark.subject)
        assertEquals("ALIVE", bookmark.state)
        assertEquals(7L, bookmark.readUpTo) // seeded from lastSeenPostNo by 6 to 7, kept by the 10 to 11 rebuild
        assertEquals(false, bookmark.pinned)
        assertNull(bookmark.lastActivityAt)

        val history = db.historyDao().all().first().single()
        assertEquals(2L, history.threadNo)
        assertEquals(5L, history.lastScrollPostNo)
        assertEquals(6L, history.maxReadPostNo)

        assertEquals(listOf(3L), db.hiddenThreadDao().all().first().map { it.threadNo })

        val saved = db.savedMediaDao().all().first().single()
        assertEquals("https://i.4cdn.org/g/1.webm", saved.url)
        assertEquals("/sdcard/Yotsuba/g/1/2_a.webm", saved.absolutePath)
        assertNull(saved.md5)
        assertNull(saved.hasAudio)
        assertNull(saved.durationMs)

        assertTrue(db.claimedPostDao().forThread("g", 9_400_000_001L).first().isEmpty())
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM downloaded_media").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
    }

    @Test fun `an empty v6 database opens at the current version without a destructive fallback`() {
        createV6 { }

        val db = openCurrent()

        db.openHelper.readableDatabase.query("PRAGMA user_version").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(currentVersion(), c.getInt(0))
        }
        db.openHelper.readableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_saved_media_md5'").use { c ->
                assertEquals(1, c.count)
            }
    }

    @Test fun `current version on a fresh install matches the exported schema`() {
        val db = openCurrent()
        val expected = schema(currentVersion())["identityHash"]!!.jsonPrimitive.content
        db.openHelper.readableDatabase.query("SELECT identity_hash FROM room_master_table").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(expected, c.getString(0))
        }
    }

    /** Opens [dbName] with the real migrations only. A schema mismatch throws IllegalStateException here. */
    private fun openCurrent(): YotsubaDatabase = Room.databaseBuilder(context, YotsubaDatabase::class.java, dbName)
        .addMigrations(*migrations)
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .allowMainThreadQueries()
        .build()
        .also {
            opened = it
            it.openHelper.writableDatabase // force onCreate/onUpgrade to run now
        }

    /** Builds the v6 schema exactly as exported by the Room compiler, then hands the db to [seed]. */
    private fun createV6(seed: (SupportSQLiteDatabase) -> Unit) {
        val schema = schema(startVersion)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(startVersion) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        for (entity in schema["entities"]!!.jsonArray) {
                            val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
                            db.execSQL(entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                            entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                                db.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                            }
                        }
                        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                        db.execSQL(
                            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, " +
                                "'${schema["identityHash"]!!.jsonPrimitive.content}')",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                        error("fresh v6 file should never upgrade here")
                })
                .build(),
        )
        helper.writableDatabase.use(seed)
        helper.close()
    }

    private fun schema(version: Int) = Json.parseToJsonElement(schemaFile(version).readText()).jsonObject["database"]!!.jsonObject

    /** Unit tests run with the module directory as cwd, but fall back to the repo layout just in case. */
    private fun schemaFile(version: Int): File {
        val name = "dev.stan.yotsuba.core.database.YotsubaDatabase/$version.json"
        return listOf(File("schemas/$name"), File("app/schemas/$name")).firstOrNull { it.exists() }
            ?: error("exported schema $name not found from ${File(".").absolutePath}")
    }

    private fun currentVersion(): Int = schemaFile(startVersion).parentFile!!.listFiles()!!
        .mapNotNull { it.nameWithoutExtension.toIntOrNull() }.max()
}

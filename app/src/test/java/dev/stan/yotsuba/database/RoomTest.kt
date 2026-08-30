package dev.stan.yotsuba.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.stan.yotsuba.core.database.YotsubaDatabase
import dev.stan.yotsuba.core.database.entity.BookmarkEntity
import dev.stan.yotsuba.core.database.entity.HiddenThreadEntity
import dev.stan.yotsuba.core.database.entity.HistoryEntity
import dev.stan.yotsuba.core.database.entity.SavedMediaEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class RoomTest {
    private lateinit var db: YotsubaDatabase

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, YotsubaDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() { db.close() }

    private fun bookmark(no: Long, state: String = "ALIVE") = BookmarkEntity(
        board = "g", threadNo = no, subject = "s", opExcerpt = "e", thumbnailUrl = null,
        replyCount = 1, imageCount = 0, bookmarkedAt = no, lastCheckedAt = null,
        lastSeenPostNo = null, state = state,
    )

    @Test fun `bookmark add, dead-marking, remove`() = runTest {
        val dao = db.bookmarkDao()
        dao.upsert(bookmark(1))
        assertTrue(dao.isBookmarked("g", 1).first())
        dao.upsert(bookmark(1, state = "DEAD")) // 404 flips to DEAD, row stays (D9)
        assertEquals("DEAD", dao.all().first().single().state)
        dao.delete("g", 1)
        assertFalse(dao.isBookmarked("g", 1).first())
    }

    @Test fun `long thread numbers round-trip through Room`() = runTest {
        val dao = db.bookmarkDao()
        dao.upsert(bookmark(9_400_000_000L))
        assertEquals(9_400_000_000L, dao.all().first().single().threadNo)
    }

    @Test fun `history upsert dedups by primary key and refreshes viewedAt`() = runTest {
        val dao = db.historyDao()
        val e = HistoryEntity("g", 1, "s", "e", null, viewedAt = 100, lastScrollPostNo = null)
        dao.record(e)
        dao.record(e.copy(viewedAt = 200))
        val all = dao.all().first()
        assertEquals(1, all.size)
        assertEquals(200L, all.single().viewedAt)
    }

    @Test fun `history retention trim`() = runTest {
        val dao = db.historyDao()
        dao.record(HistoryEntity("g", 1, null, "old", null, viewedAt = 100, lastScrollPostNo = null))
        dao.record(HistoryEntity("g", 2, null, "new", null, viewedAt = 900, lastScrollPostNo = null))
        dao.trimOlderThan(500)
        assertEquals(listOf(2L), dao.all().first().map { it.threadNo })
    }

    @Test fun `hidden threads hide and unhide`() = runTest {
        val dao = db.hiddenThreadDao()
        dao.hide(HiddenThreadEntity("g", 42, 1))
        assertEquals(1, dao.all().first().size)
        dao.unhide("g", 42)
        assertTrue(dao.all().first().isEmpty())
    }

    @Test fun `markSeen only rises and writes nothing when it would not`() = runTest {
        val dao = db.bookmarkDao()
        dao.upsert(bookmark(1))
        dao.markSeen("g", 1, 10)
        assertEquals(10L, dao.all().first().single().readUpTo)
        dao.markSeen("g", 1, 5)
        assertEquals(10L, dao.all().first().single().readUpTo)
        dao.markSeen("g", 1, 12)
        assertEquals(12L, dao.all().first().single().readUpTo)
    }

    private fun saved(url: String, ext: String?, md5: String?, phash: Long?, path: String = "/v/$url") = SavedMediaEntity(
        url = url, board = "g", threadNo = 1, postNo = 1, subject = null, displayName = url,
        absolutePath = path, ext = ext, sizeBytes = null, width = null, height = null,
        thumbnailUrl = null, savedAt = 0, md5 = md5, phash = phash,
    )

    @Test fun `missingHashCount agrees with missingHashes`() = runTest {
        val dao = db.savedMediaDao()
        dao.insertAll(
            listOf(
                saved("a.jpg", ".jpg", md5 = null, phash = null),
                saved("b.jpg", ".jpg", md5 = "m", phash = null),
                saved("c.jpg", ".jpg", md5 = "m", phash = 1L),
                saved("d.webm", ".webm", md5 = "m", phash = null),
                saved("e.webm", ".webm", md5 = null, phash = null),
                saved("f.jpg", ".jpg", md5 = null, phash = null, path = ""),
            ),
        )
        val rows = dao.missingHashes()
        assertEquals(setOf("a.jpg", "b.jpg", "e.webm"), rows.map { it.url }.toSet())
        assertEquals(rows.size, dao.missingHashCount())
    }
}

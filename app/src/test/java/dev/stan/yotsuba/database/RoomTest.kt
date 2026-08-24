package dev.stan.yotsuba.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.stan.yotsuba.core.database.YotsubaDatabase
import dev.stan.yotsuba.core.database.entity.BookmarkEntity
import dev.stan.yotsuba.core.database.entity.HiddenThreadEntity
import dev.stan.yotsuba.core.database.entity.HistoryEntity
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
        dao.upsert(e)
        dao.upsert(e.copy(viewedAt = 200))
        val all = dao.all().first()
        assertEquals(1, all.size)
        assertEquals(200L, all.single().viewedAt)
    }

    @Test fun `history retention trim`() = runTest {
        val dao = db.historyDao()
        dao.upsert(HistoryEntity("g", 1, null, "old", null, viewedAt = 100, lastScrollPostNo = null))
        dao.upsert(HistoryEntity("g", 2, null, "new", null, viewedAt = 900, lastScrollPostNo = null))
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
}

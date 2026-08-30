package dev.stan.yotsuba.data

import dev.stan.yotsuba.core.database.dao.BookmarkDao
import dev.stan.yotsuba.core.database.entity.BookmarkEntity
import dev.stan.yotsuba.core.network.FourChanApi
import dev.stan.yotsuba.core.network.dto.BoardsDto
import dev.stan.yotsuba.core.network.dto.CatalogPageDto
import dev.stan.yotsuba.core.network.dto.PostDto
import dev.stan.yotsuba.core.network.dto.ThreadDto
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.data.repository.BookmarkRepositoryImpl
import dev.stan.yotsuba.data.repository.toDomain
import dev.stan.yotsuba.data.repository.toEntity
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.core.text.PostText
import dev.stan.yotsuba.domain.repository.CatalogRepository
import java.net.SocketTimeoutException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class BookmarkRepositoryImplTest {

    /** In-memory table; every UPDATE is the targeted one the real DAO issues. */
    private class FakeBookmarkDao(initial: List<BookmarkEntity> = emptyList()) : BookmarkDao {
        val rows = MutableStateFlow(initial)
        var refreshArgs: List<Any?>? = null

        private fun update(board: String, threadNo: Long, f: (BookmarkEntity) -> BookmarkEntity) {
            rows.value = rows.value.map { if (it.board == board && it.threadNo == threadNo) f(it) else it }
        }

        override fun all(): Flow<List<BookmarkEntity>> = rows
        override suspend fun upsert(entity: BookmarkEntity) {
            rows.value = rows.value.filterNot { it.board == entity.board && it.threadNo == entity.threadNo } + entity
        }
        override suspend fun delete(board: String, threadNo: Long) {
            rows.value = rows.value.filterNot { it.board == board && it.threadNo == threadNo }
        }
        override fun isBookmarked(board: String, threadNo: Long): Flow<Boolean> =
            rows.map { list -> list.any { it.board == board && it.threadNo == threadNo } }
        override suspend fun markSeen(board: String, threadNo: Long, postNo: Long) =
            update(board, threadNo) { it.copy(readUpTo = maxOf(it.readUpTo ?: 0, postNo)) }
        override suspend fun updateRefresh(
            board: String, threadNo: Long, replyCount: Int, imageCount: Int,
            state: String, lastCheckedAt: Long?, lastActivityAt: Long?, postNos: String,
        ) {
            refreshArgs = listOf(board, threadNo, replyCount, imageCount, state, lastCheckedAt, lastActivityAt, postNos)
            update(board, threadNo) {
                it.copy(
                    replyCount = replyCount, imageCount = imageCount, state = state,
                    lastCheckedAt = lastCheckedAt, lastActivityAt = lastActivityAt, postNos = postNos,
                )
            }
        }
        override suspend fun updateCounts(
            board: String, threadNo: Long, replyCount: Int, imageCount: Int,
            state: String, lastCheckedAt: Long?, lastActivityAt: Long?,
        ) = update(board, threadNo) {
            it.copy(
                replyCount = replyCount, imageCount = imageCount, state = state,
                lastCheckedAt = lastCheckedAt, lastActivityAt = lastActivityAt,
            )
        }
        override suspend fun updateState(board: String, threadNo: Long, state: String, lastCheckedAt: Long?) =
            update(board, threadNo) { it.copy(state = state, lastCheckedAt = lastCheckedAt) }
        override suspend fun setPinned(board: String, threadNo: Long, pinned: Boolean) =
            update(board, threadNo) { it.copy(pinned = pinned) }
        override suspend fun deleteDead() { rows.value = rows.value.filterNot { it.state == "DEAD" } }
        override suspend fun clearAll() { rows.value = emptyList() }
    }

    private open class FakeApi : FourChanApi {
        val threadCalls = mutableListOf<Long>()
        override suspend fun boards(cacheControl: String?): BoardsDto = throw UnsupportedOperationException()
        override suspend fun catalog(board: String, cacheControl: String?): List<CatalogPageDto> =
            throw UnsupportedOperationException()
        override suspend fun thread(board: String, no: Long, cacheControl: String?): ThreadDto {
            threadCalls += no
            return threadFor(no)
        }
        open fun threadFor(no: Long): ThreadDto = throw UnsupportedOperationException()
    }

    private fun threadApi(dto: ThreadDto) = object : FakeApi() {
        override fun threadFor(no: Long) = dto
    }

    private fun failingApi(t: Throwable) = object : FakeApi() {
        override fun threadFor(no: Long): ThreadDto = throw t
    }

    private class FakeCatalog(private val boards: Map<String, List<CatalogThread>>) : CatalogRepository {
        val calls = mutableListOf<Pair<String, Boolean>>()
        override suspend fun catalog(board: String, forceRefresh: Boolean): DataResult<List<CatalogThread>> {
            calls += board to forceRefresh
            val list = boards[board] ?: return DataResult.Failure(NetworkError.NotFound)
            return DataResult.Success(list)
        }
    }


    private fun bookmark(
        no: Long = 100,
        board: String = "g",
        readUpTo: Long? = null,
        postNos: List<Long> = emptyList(),
        replyCount: Int = 3,
        state: BookmarkState = BookmarkState.ALIVE,
    ) = Bookmark(
        board = board, threadNo = no, subject = "sub", opExcerpt = "op", thumbnailUrl = null,
        replyCount = replyCount, imageCount = 1, bookmarkedAt = 0L, lastCheckedAt = null,
        lastSeenPostNo = null, state = state, readUpTo = readUpTo, postNos = postNos,
    )

    private fun catalogEntry(no: Long, board: String = "g", replies: Int) = CatalogThread(
        board = board, no = no, subject = null, excerpt = PostText.Empty, thumbnailUrl = null,
        replyCount = replies, imageCount = 0, lastModified = 1_700_000L, sticky = false, closed = false,
    )

    private fun threadDto(op: Long = 100, archived: Int? = null, replies: List<Long> = listOf(101, 102, 103, 104, 105)) =
        ThreadDto(
            posts = listOf(PostDto(no = op, resto = 0, replies = replies.size, images = 2, archived = archived)) +
                replies.map { PostDto(no = it, resto = op) },
        )

    private fun repo(
        api: FourChanApi,
        dao: FakeBookmarkDao = FakeBookmarkDao(),
        catalog: CatalogRepository = FakeCatalog(emptyMap()),
    ) = BookmarkRepositoryImpl(dao, api, catalog, clock = { 42L })

    // --- unread derivation -------------------------------------------------------------

    @Test fun `unread is the posts numbered past readUpTo`() {
        val b = bookmark(readUpTo = 102, postNos = listOf(101, 102, 103, 104, 105))
        assertEquals(3, b.unread)
    }

    @Test fun `unread is zero until the thread has been opened`() {
        assertEquals(0, bookmark(readUpTo = null, postNos = listOf(101, 102)).unread)
    }

    @Test fun `post numbers survive the entity round trip`() {
        val b = bookmark(readUpTo = 101, postNos = listOf(101, 102, 103), state = BookmarkState.ARCHIVED).copy(pinned = true)
        assertEquals(b, b.toEntity().toDomain())
    }

    @Test fun `markSeen only ever raises the read mark and touches nothing else`() = runTest {
        val dao = FakeBookmarkDao(listOf(bookmark(readUpTo = 103, postNos = listOf(101, 102, 103, 104)).toEntity()))
        val r = repo(threadApi(threadDto()), dao)
        r.markSeen("g", 100, 101)
        assertEquals(103L, dao.rows.value.single().readUpTo)
        r.markSeen("g", 100, 104)
        val row = dao.rows.value.single()
        assertEquals(104L, row.readUpTo)
        assertEquals(3, row.replyCount)
        assertEquals("101,102,103,104", row.postNos)
    }

    // --- refreshOne ------------------------------------------------------------------------

    @Test fun `refreshOne learns the post list and counts`() = runTest {
        val refreshed = repo(threadApi(threadDto())).refreshOne(bookmark(readUpTo = 102))
        assertEquals(listOf(101L, 102L, 103L, 104L, 105L), refreshed.postNos)
        assertEquals(3, refreshed.unread)
        assertEquals(5, refreshed.replyCount)
        assertEquals(2, refreshed.imageCount)
        assertEquals(BookmarkState.ALIVE, refreshed.state)
    }

    @Test fun `archived thread becomes ARCHIVED, not DEAD`() = runTest {
        val refreshed = repo(threadApi(threadDto(archived = 1))).refreshOne(bookmark())
        assertEquals(BookmarkState.ARCHIVED, refreshed.state)
    }

    @Test fun `404 marks the bookmark DEAD but keeps the snapshot counts`() = runTest {
        val dao = FakeBookmarkDao()
        val refreshed = repo(failingApi(http404()), dao).refreshOne(bookmark(readUpTo = 102))
        assertEquals(BookmarkState.DEAD, refreshed.state)
        assertEquals(3, refreshed.replyCount)
        assertNotNull(refreshed.lastCheckedAt)
        assertEquals("DEAD", dao.refreshArgs?.get(4))
    }

    @Test fun `transient failure leaves the bookmark unchanged but still writes the refresh row`() = runTest {
        val dao = FakeBookmarkDao()
        val original = bookmark(readUpTo = 102)
        val refreshed = repo(failingApi(SocketTimeoutException()), dao).refreshOne(original)
        assertEquals(original, refreshed)
        assertNull(refreshed.lastCheckedAt)
        assertEquals("ALIVE", dao.refreshArgs?.get(4))
    }

    @Test fun `refreshOne never writes readUpTo or pinned`() = runTest {
        val dao = FakeBookmarkDao(listOf(bookmark(readUpTo = 101).copy(pinned = true).toEntity()))
        val r = repo(threadApi(threadDto()), dao)
        // A markSeen landing mid-refresh is the race the DAO comment promises to survive.
        r.markSeen("g", 100, 104)
        r.refreshOne(bookmark(readUpTo = 101))
        val row = dao.rows.value.single()
        assertEquals(104L, row.readUpTo)
        assertTrue(row.pinned)
        assertEquals(5, row.replyCount)
    }

    // --- refreshAll ------------------------------------------------------------------------

    @Test fun `refreshAll issues one forced catalog call per board`() = runTest {
        val dao = FakeBookmarkDao(
            listOf(
                bookmark(no = 1, board = "g", postNos = listOf(2), replyCount = 1),
                bookmark(no = 3, board = "g", postNos = listOf(4), replyCount = 1),
                bookmark(no = 5, board = "a", postNos = listOf(6), replyCount = 1),
            ).map { it.toEntity() },
        )
        val catalog = FakeCatalog(
            mapOf(
                "g" to listOf(catalogEntry(1, replies = 1), catalogEntry(3, replies = 1)),
                "a" to listOf(catalogEntry(5, board = "a", replies = 1)),
            ),
        )
        val api = FakeApi()
        val progress = mutableListOf<Pair<Int, Int>>()
        val summary = repo(api, dao, catalog).refreshAll { d, t -> progress += d to t }
        assertEquals(listOf("g" to true, "a" to true), catalog.calls)
        assertTrue(api.threadCalls.isEmpty()) // nothing moved: no thread JSON
        assertEquals(3, summary.threadsChecked)
        assertEquals(listOf(0 to 2, 1 to 2, 2 to 2), progress)
    }

    @Test fun `refreshAll fetches the thread only when the reply count moved`() = runTest {
        val dao = FakeBookmarkDao(
            listOf(
                bookmark(no = 100, readUpTo = 102, postNos = listOf(101, 102), replyCount = 2),
                bookmark(no = 200, readUpTo = 201, postNos = listOf(201), replyCount = 1),
            ).map { it.toEntity() },
        )
        val catalog = FakeCatalog(mapOf("g" to listOf(catalogEntry(100, replies = 5), catalogEntry(200, replies = 1))))
        val api = threadApi(threadDto())
        val summary = repo(api, dao, catalog).refreshAll()
        assertEquals(listOf(100L), api.threadCalls)
        val moved = dao.rows.value.first { it.threadNo == 100L }
        assertEquals("101,102,103,104,105", moved.postNos)
        assertEquals(3, moved.toDomain().unread)
        assertEquals(3, summary.newUnread)
        assertEquals(1, summary.threadsWithNew)
        assertEquals(1_700_000_000L, moved.lastActivityAt)
    }

    @Test fun `absent from the catalog and 404 marks the thread DEAD`() = runTest {
        val dao = FakeBookmarkDao(listOf(bookmark(no = 100, postNos = listOf(101), replyCount = 1).toEntity()))
        val catalog = FakeCatalog(mapOf("g" to emptyList()))
        repo(failingApi(http404()), dao, catalog).refreshAll()
        assertEquals("DEAD", dao.rows.value.single().state)
    }

    @Test fun `absent from the catalog but still fetchable as archived marks ARCHIVED`() = runTest {
        val dao = FakeBookmarkDao(listOf(bookmark(no = 100, postNos = listOf(101), replyCount = 1).toEntity()))
        val catalog = FakeCatalog(mapOf("g" to emptyList()))
        repo(threadApi(threadDto(archived = 1)), dao, catalog).refreshAll()
        assertEquals("ARCHIVED", dao.rows.value.single().state)
    }

    @Test fun `dead and archived rows are skipped on every refresh`() = runTest {
        val dao = FakeBookmarkDao(
            listOf(
                bookmark(no = 1, board = "g", state = BookmarkState.DEAD),
                bookmark(no = 2, board = "a", state = BookmarkState.ARCHIVED),
            ).map { it.toEntity() },
        )
        val catalog = FakeCatalog(emptyMap())
        val api = FakeApi()
        val summary = repo(api, dao, catalog).refreshAll()
        assertTrue(catalog.calls.isEmpty())
        assertTrue(api.threadCalls.isEmpty())
        assertEquals(0, summary.threadsChecked)
    }

    @Test fun `removeDead drops pruned rows and keeps archived ones`() = runTest {
        val dao = FakeBookmarkDao(
            listOf(
                bookmark(no = 1, state = BookmarkState.DEAD),
                bookmark(no = 2, state = BookmarkState.ARCHIVED),
                bookmark(no = 3),
            ).map { it.toEntity() },
        )
        repo(FakeApi(), dao).removeDead()
        assertEquals(listOf(2L, 3L), dao.rows.value.map { it.threadNo })
    }

    private fun http404() = HttpException(
        Response.error<Any>(404, "".toResponseBody("application/json".toMediaType())),
    )
}

package dev.stan.yotsuba.data

import dev.stan.yotsuba.core.database.dao.BookmarkDao
import dev.stan.yotsuba.core.database.dao.HistoryDao
import dev.stan.yotsuba.core.database.entity.BookmarkEntity
import dev.stan.yotsuba.core.database.entity.HistoryEntity
import dev.stan.yotsuba.core.network.FourChanApi
import dev.stan.yotsuba.core.network.dto.BoardsDto
import dev.stan.yotsuba.core.network.dto.CatalogPageDto
import dev.stan.yotsuba.core.network.dto.PostDto
import dev.stan.yotsuba.core.network.dto.ThreadDto
import dev.stan.yotsuba.data.repository.BookmarkRepositoryImpl
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import java.net.SocketTimeoutException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class BookmarkRepositoryImplTest {

    private class FakeBookmarkDao : BookmarkDao {
        var refreshArgs: List<Any?>? = null
        override fun all(): Flow<List<BookmarkEntity>> = flowOf(emptyList())
        override suspend fun upsert(entity: BookmarkEntity) {}
        override suspend fun delete(board: String, threadNo: Long) {}
        override fun isBookmarked(board: String, threadNo: Long): Flow<Boolean> = flowOf(false)
        override suspend fun markSeen(board: String, threadNo: Long, lastSeenPostNo: Long, replyCount: Int) {}
        override suspend fun updateRefresh(
            board: String, threadNo: Long, replyCount: Int, imageCount: Int,
            state: String, lastCheckedAt: Long?, newReplies: Int, unreadCount: Int,
        ) {
            refreshArgs = listOf(board, threadNo, replyCount, imageCount, state, lastCheckedAt, newReplies, unreadCount)
        }
        override suspend fun updateUnread(board: String, threadNo: Long, unread: Int) {}
        override suspend fun clearAll() {}
    }

    private class FakeHistoryDao(var maxReadPostNo: Long? = null) : HistoryDao {
        override fun all(): Flow<List<HistoryEntity>> = flowOf(emptyList())
        override suspend fun updateVisit(
            board: String, threadNo: Long, subject: String?,
            opExcerpt: String, thumbnailUrl: String?, viewedAt: Long,
        ): Int = 0
        override suspend fun insertIgnore(entity: HistoryEntity) {}
        override suspend fun lastScroll(board: String, threadNo: Long): Long? = null
        override suspend fun updateScroll(board: String, threadNo: Long, postNo: Long) {}
        override suspend fun updateMaxRead(board: String, threadNo: Long, postNo: Long) {}
        override suspend fun maxRead(board: String, threadNo: Long): Long? = maxReadPostNo
        override suspend fun delete(board: String, threadNo: Long) {}
        override suspend fun clearAll() {}
        override suspend fun trimOlderThan(cutoffMs: Long) {}
    }

    private open class FakeApi : FourChanApi {
        override suspend fun boards(cacheControl: String?): BoardsDto = throw UnsupportedOperationException()
        override suspend fun catalog(board: String, cacheControl: String?): List<CatalogPageDto> =
            throw UnsupportedOperationException()
        override suspend fun thread(board: String, no: Long, cacheControl: String?): ThreadDto =
            throw UnsupportedOperationException()
    }

    private fun threadApi(dto: ThreadDto) = object : FakeApi() {
        override suspend fun thread(board: String, no: Long, cacheControl: String?) = dto
    }

    private fun failingApi(t: Throwable) = object : FakeApi() {
        override suspend fun thread(board: String, no: Long, cacheControl: String?): ThreadDto = throw t
    }

    private fun bookmark(lastSeenPostNo: Long? = null) = Bookmark(
        board = "g", threadNo = 100, subject = "sub", opExcerpt = "op", thumbnailUrl = null,
        replyCount = 3, imageCount = 1, bookmarkedAt = 0L, lastCheckedAt = null,
        lastSeenPostNo = lastSeenPostNo, state = BookmarkState.ALIVE,
        newReplies = 2, unreadCount = 2,
    )

    private fun threadDto(archived: Int? = null) = ThreadDto(
        posts = listOf(
            PostDto(no = 100, resto = 0, replies = 5, images = 2, archived = archived),
            PostDto(no = 101), PostDto(no = 102), PostDto(no = 103),
            PostDto(no = 104), PostDto(no = 105),
        )
    )

    private fun repo(api: FourChanApi, history: FakeHistoryDao = FakeHistoryDao(), dao: FakeBookmarkDao = FakeBookmarkDao()) =
        BookmarkRepositoryImpl(dao, history, api)

    @Test fun `new replies are the posts past the last-seen marker`() = runTest {
        val refreshed = repo(threadApi(threadDto())).refreshOne(bookmark(lastSeenPostNo = 102))
        assertEquals(3, refreshed.newReplies) // 103, 104, 105
        assertEquals(5, refreshed.replyCount)
        assertEquals(2, refreshed.imageCount)
        assertEquals(BookmarkState.ALIVE, refreshed.state)
    }

    @Test fun `unread count prefers the read high-water mark over the last-seen marker`() = runTest {
        val refreshed = repo(threadApi(threadDto()), history = FakeHistoryDao(maxReadPostNo = 104))
            .refreshOne(bookmark(lastSeenPostNo = 102))
        assertEquals(3, refreshed.newReplies) // still relative to lastSeen
        assertEquals(1, refreshed.unreadCount) // only 105 is past the read mark
    }

    @Test fun `unread falls back to last-seen when there is no read mark`() = runTest {
        val refreshed = repo(threadApi(threadDto())).refreshOne(bookmark(lastSeenPostNo = 103))
        assertEquals(2, refreshed.unreadCount) // 104, 105
    }

    @Test fun `never-opened bookmark refreshes to zero new and unread`() = runTest {
        val refreshed = repo(threadApi(threadDto())).refreshOne(bookmark(lastSeenPostNo = null))
        assertEquals(0, refreshed.newReplies)
        assertEquals(0, refreshed.unreadCount)
    }

    @Test fun `archived thread flips the bookmark to DEAD`() = runTest {
        val refreshed = repo(threadApi(threadDto(archived = 1))).refreshOne(bookmark())
        assertEquals(BookmarkState.DEAD, refreshed.state)
    }

    @Test fun `404 marks the bookmark DEAD but keeps the snapshot counts`() = runTest {
        val dao = FakeBookmarkDao()
        val http404 = HttpException(
            Response.error<Any>(404, "".toResponseBody("application/json".toMediaType()))
        )
        val refreshed = repo(failingApi(http404), dao = dao).refreshOne(bookmark(lastSeenPostNo = 102))
        assertEquals(BookmarkState.DEAD, refreshed.state)
        assertEquals(3, refreshed.replyCount)
        assertEquals(2, refreshed.newReplies)
        assertNotNull(refreshed.lastCheckedAt)
        assertEquals("DEAD", dao.refreshArgs?.get(4))
    }

    @Test fun `transient failure leaves the bookmark unchanged but still writes the refresh row`() = runTest {
        val dao = FakeBookmarkDao()
        val original = bookmark(lastSeenPostNo = 102)
        val refreshed = repo(failingApi(SocketTimeoutException()), dao = dao).refreshOne(original)
        assertEquals(original, refreshed)
        assertNull(refreshed.lastCheckedAt)
        assertEquals("ALIVE", dao.refreshArgs?.get(4))
    }
}

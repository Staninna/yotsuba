package dev.stan.yotsuba.data

import dev.stan.yotsuba.core.network.ArchiveApi
import dev.stan.yotsuba.core.network.FourChanApi
import dev.stan.yotsuba.core.network.dto.BoardDto
import dev.stan.yotsuba.core.network.dto.BoardsDto
import dev.stan.yotsuba.core.network.dto.CatalogPageDto
import dev.stan.yotsuba.core.network.dto.PostDto
import dev.stan.yotsuba.core.network.dto.ThreadDto
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.data.repository.BoardRepositoryImpl
import dev.stan.yotsuba.data.repository.CatalogRepositoryImpl
import dev.stan.yotsuba.data.repository.ThreadRepositoryImpl
import java.net.UnknownHostException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryTest {

    private open class FakeApi : FourChanApi {
        var boardsCalls = 0
        override suspend fun boards(cacheControl: String?): BoardsDto = throw UnsupportedOperationException()
        override suspend fun catalog(board: String, cacheControl: String?): List<CatalogPageDto> =
            throw UnsupportedOperationException()
        override suspend fun thread(board: String, no: Long, cacheControl: String?): ThreadDto =
            throw UnsupportedOperationException()
    }

    private fun boardsApi(dto: BoardsDto) = object : FakeApi() {
        override suspend fun boards(cacheControl: String?): BoardsDto {
            boardsCalls++
            return dto
        }
    }

    private val boardsDto = BoardsDto(
        boards = listOf(
            BoardDto(board = "g", title = "Technology"),
            BoardDto(board = "f", title = "Flash"),
            BoardDto(board = "a", title = "Anime"),
        )
    )

    // BoardRepositoryImpl

    @Test fun `second boards call is served from cache without hitting the api`() = runTest {
        val api = boardsApi(boardsDto)
        val repo = BoardRepositoryImpl(api)

        val first = repo.boards()
        assertTrue(first is DataResult.Success && !first.fromCache)
        val second = repo.boards()
        assertTrue(second is DataResult.Success && (second as DataResult.Success).fromCache)
        assertEquals(1, api.boardsCalls)
    }

    @Test fun `forceRefresh bypasses the cache`() = runTest {
        val api = boardsApi(boardsDto)
        val repo = BoardRepositoryImpl(api)
        repo.boards()
        val refreshed = repo.boards(forceRefresh = true)
        assertTrue(refreshed is DataResult.Success && !(refreshed as DataResult.Success).fromCache)
        assertEquals(2, api.boardsCalls)
    }

    @Test fun `f board is filtered out`() = runTest {
        val repo = BoardRepositoryImpl(boardsApi(boardsDto))
        val result = repo.boards() as DataResult.Success
        assertEquals(listOf("g", "a"), result.value.map { it.code })
    }

    @Test fun `board lookup by code`() = runTest {
        val repo = BoardRepositoryImpl(boardsApi(boardsDto))
        assertEquals("Technology", repo.board("g")?.title)
        assertNull(repo.board("f"))
        assertNull(repo.board("zzz"))
    }

    @Test fun `boards failure maps to NetworkError`() = runTest {
        val api = object : FakeApi() {
            override suspend fun boards(cacheControl: String?): BoardsDto = throw UnknownHostException()
        }
        val repo = BoardRepositoryImpl(api)
        assertEquals(DataResult.Failure(NetworkError.Offline), repo.boards())
        assertNull(repo.board("g"))
    }

    // CatalogRepositoryImpl

    @Test fun `catalog pages are flattened into CatalogThread list`() = runTest {
        val api = object : FakeApi() {
            override suspend fun catalog(board: String, cacheControl: String?) = listOf(
                CatalogPageDto(page = 1, threads = listOf(PostDto(no = 1, sub = "first"), PostDto(no = 2))),
                CatalogPageDto(page = 2, threads = listOf(PostDto(no = 3))),
            )
        }
        val result = CatalogRepositoryImpl(api).catalog("g") as DataResult.Success
        assertEquals(listOf(1L, 2L, 3L), result.value.map { it.no })
        assertTrue(result.value.all { it.board == "g" })
        assertEquals("first", result.value[0].subject)
    }

    @Test fun `catalog failure maps to NetworkError`() = runTest {
        val api = object : FakeApi() {
            override suspend fun catalog(board: String, cacheControl: String?): List<CatalogPageDto> =
                throw java.net.SocketTimeoutException()
        }
        assertEquals(DataResult.Failure(NetworkError.Timeout), CatalogRepositoryImpl(api).catalog("g"))
    }

    // ThreadRepositoryImpl

    @Test fun `thread posts are mapped and OP flags carried over`() = runTest {
        val api = object : FakeApi() {
            override suspend fun thread(board: String, no: Long, cacheControl: String?) = ThreadDto(
                posts = listOf(
                    PostDto(no = 100, resto = 0, archived = 1, closed = 1, com = "op"),
                    PostDto(
                        no = 101, resto = 100,
                        com = "<a href=\"#p100\" class=\"quotelink\">&gt;&gt;100</a>",
                    ),
                )
            )
        }
        val result = ThreadRepositoryImpl(api, NoArchive).thread("g", 100) as DataResult.Success
        val details = result.value
        assertEquals("g", details.board)
        assertEquals(100L, details.threadNo)
        assertEquals(2, details.posts.size)
        assertTrue(details.posts[0].isOp)
        assertTrue(details.archived)
        assertTrue(details.closed)
        // >>100 in post 101 shows up as a backlink on 100.
        assertEquals(listOf(101L), details.backlinks[100L])
    }

    @Test fun `thread failure maps to NetworkError`() = runTest {
        val api = object : FakeApi() {
            override suspend fun thread(board: String, no: Long, cacheControl: String?): ThreadDto =
                throw java.net.ConnectException()
        }
        assertEquals(DataResult.Failure(NetworkError.Offline), ThreadRepositoryImpl(api, NoArchive).thread("g", 1))
    }
}

/** An archive that never answers: these tests are about the live API. */
private object NoArchive : ArchiveApi {
    override suspend fun thread(url: String) = kotlinx.serialization.json.JsonObject(emptyMap())
}

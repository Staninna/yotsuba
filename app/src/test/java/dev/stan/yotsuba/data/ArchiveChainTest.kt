package dev.stan.yotsuba.data

import dev.stan.yotsuba.core.network.ArchiveApi
import dev.stan.yotsuba.core.network.FourChanApi
import dev.stan.yotsuba.data.repository.ThreadRepositoryImpl
import dev.stan.yotsuba.domain.model.ArchiveSource
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.fake.NoUsage
import java.net.UnknownHostException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** /vr/ is carried by desuarchive, then archived.moe, then warosu (no API), so it walks the whole chain. */
class ArchiveChainTest {

    private val thread = Json.parseToJsonElement(
        """{"1":{"op":{"num":"1","timestamp":"0","op":"1","comment":"hi"},"posts":{}}}""",
    ) as JsonObject
    private val notFound = Json.parseToJsonElement("""{"error":"Thread not found."}""") as JsonObject

    private class Api(private val answer: (host: String) -> JsonObject) : ArchiveApi {
        val asked = mutableListOf<String>()
        override suspend fun thread(url: String): JsonObject {
            val host = url.removePrefix("https://").substringBefore('/')
            asked += host
            return answer(host)
        }
    }

    private fun repo(api: Api) = ThreadRepositoryImpl(NoLive, api, NoUsage)

    @Test fun `a miss at the first archive moves on to the next`() = runTest {
        val api = Api { host -> if (host == "archived.moe") thread else notFound }
        val r = repo(api).archivedThread("vr", 1) as DataResult.Success
        assertEquals(ArchiveSource.ARCHIVED_MOE, r.value.archive)
        assertEquals(listOf("desuarchive.org", "archived.moe"), api.asked)
    }

    @Test fun `a failed request moves on too and the first hit wins`() = runTest {
        val api = Api { host -> if (host == "desuarchive.org") throw UnknownHostException() else thread }
        val r = repo(api).archivedThread("vr", 1) as DataResult.Success
        assertEquals(ArchiveSource.ARCHIVED_MOE, r.value.archive)
    }

    @Test fun `a rate limit stops the chain`() = runTest {
        val api = Api { throw HttpException(Response.error<Any>(429, "".toResponseBody())) }
        assertEquals(DataResult.Failure(NetworkError.RateLimited), repo(api).archivedThread("vr", 1))
        assertEquals(listOf("desuarchive.org"), api.asked)
    }

    @Test fun `every miss is not found and warosu is never asked`() = runTest {
        val api = Api { notFound }
        assertEquals(DataResult.Failure(NetworkError.NotFound), repo(api).archivedThread("vr", 1))
        assertEquals(listOf("desuarchive.org", "archived.moe"), api.asked)
    }

    @Test fun `an unarchived board is not found without a request`() = runTest {
        val api = Api { thread }
        assertEquals(DataResult.Failure(NetworkError.NotFound), repo(api).archivedThread("zzz", 1))
        assertEquals(emptyList<String>(), api.asked)
    }
}

/** These tests are about the archives; the live API must never be asked. */
private object NoLive : FourChanApi {
    override suspend fun boards(cacheControl: String?) = throw UnsupportedOperationException()
    override suspend fun catalog(board: String, cacheControl: String?) = throw UnsupportedOperationException()
    override suspend fun thread(board: String, no: Long, cacheControl: String?) = throw UnsupportedOperationException()
}

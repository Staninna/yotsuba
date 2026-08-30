package dev.stan.yotsuba.util

import dev.stan.yotsuba.core.util.apiResult
import dev.stan.yotsuba.core.util.toNetworkError
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.NetworkError
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ApiResultTest {

    @Test fun `success wraps the block result`() = runTest {
        assertEquals(DataResult.Success(42), apiResult { 42 })
    }

    @Test fun `exception maps to Failure via toNetworkError`() = runTest {
        val result = apiResult<Int> { throw SocketTimeoutException("slow") }
        assertEquals(DataResult.Failure(NetworkError.Timeout), result)
    }

    @Test fun `CancellationException is rethrown, not captured`() = runTest {
        try {
            apiResult<Int> { throw CancellationException("cancelled") }
            fail("expected CancellationException to propagate")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    private fun httpException(code: Int): HttpException = HttpException(
        Response.error<Any>(code, "".toResponseBody("text/plain".toMediaType()))
    )

    @Test fun `socket timeout maps to Timeout`() {
        assertEquals(NetworkError.Timeout, SocketTimeoutException().toNetworkError())
    }

    @Test fun `unknown host and connect map to Offline`() {
        assertEquals(NetworkError.Offline, UnknownHostException().toNetworkError())
        assertEquals(NetworkError.Offline, ConnectException().toNetworkError())
    }

    @Test fun `http 404 maps to NotFound`() {
        assertEquals(NetworkError.NotFound, httpException(404).toNetworkError())
    }

    @Test fun `http 429 maps to RateLimited`() {
        assertEquals(NetworkError.RateLimited, httpException(429).toNetworkError())
    }

    @Test fun `http 5xx maps to Server with code`() {
        assertEquals(NetworkError.Server(500), httpException(500).toNetworkError())
        assertEquals(NetworkError.Server(503), httpException(503).toNetworkError())
        assertEquals(NetworkError.Server(599), httpException(599).toNetworkError())
    }

    @Test fun `other http codes map to Unknown carrying the exception`() {
        val e = httpException(418)
        val error = e.toNetworkError()
        assertTrue(error is NetworkError.Unknown)
        assertEquals(e, (error as NetworkError.Unknown).cause)
    }

    @Test fun `arbitrary exceptions map to Unknown`() {
        val e = IllegalStateException("boom")
        assertEquals(NetworkError.Unknown(e), e.toNetworkError())
    }
}

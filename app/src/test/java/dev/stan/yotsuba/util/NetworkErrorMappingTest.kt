package dev.stan.yotsuba.util

import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.core.util.toNetworkError
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class NetworkErrorMappingTest {

    private fun http(code: Int) = HttpException(
        Response.error<Any>(code, "".toResponseBody("application/json".toMediaType()))
    )

    @Test fun `timeouts map to Timeout`() {
        assertEquals(NetworkError.Timeout, SocketTimeoutException().toNetworkError())
    }

    @Test fun `dns and connect failures map to Offline`() {
        assertEquals(NetworkError.Offline, UnknownHostException().toNetworkError())
        assertEquals(NetworkError.Offline, ConnectException().toNetworkError())
    }

    @Test fun `http status codes map to their own errors`() {
        assertEquals(NetworkError.NotFound, http(404).toNetworkError())
        assertEquals(NetworkError.RateLimited, http(429).toNetworkError())
        assertEquals(NetworkError.Server(500), http(500).toNetworkError())
        assertEquals(NetworkError.Server(599), http(599).toNetworkError())
    }

    @Test fun `other http codes and exceptions fall through to Unknown`() {
        assertTrue(http(418).toNetworkError() is NetworkError.Unknown)
        val cause = IllegalStateException("boom")
        val mapped = cause.toNetworkError()
        assertTrue(mapped is NetworkError.Unknown && mapped.cause === cause)
    }
}

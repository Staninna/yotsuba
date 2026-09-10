package dev.stan.yotsuba.network

import dev.stan.yotsuba.core.network.DataUsageMeter
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataUsageMeterTest {

    private fun boardOf(url: String) = DataUsageMeter.boardOf(url.toHttpUrl())

    @Test fun `reads the board off 4chan and archive URLs and nothing off the rest`() {
        assertEquals("g", boardOf("https://a.4cdn.org/g/catalog.json"))
        assertEquals("g", boardOf("https://i.4cdn.org/g/1699123s.jpg"))
        assertNull(boardOf("https://a.4cdn.org/boards.json"))
        assertEquals("vr", boardOf("https://desuarchive.org/_/api/chan/thread/?board=vr&num=1"))
        assertEquals("pol", boardOf("https://i.4pcdn.org/pol/1699123.jpg"))
        assertEquals("v", boardOf("https://arch.b4k.dev/files/v/image/1699/12/1699123.png"))
        assertNull(boardOf("https://example.com/"))
    }

    @Test fun `counts chunked bodies on the way through and batches per board`() {
        val server = MockWebServer().apply { start() }
        val batches = mutableListOf<Pair<String?, Long>>()
        // Threshold lands exactly on the end of the second body, so the flush point does not
        // depend on how OkHttp slices the chunked reads.
        val meter = DataUsageMeter({ board, bytes -> batches += board to bytes }, flushBytes = 60)
        val client = OkHttpClient.Builder().addNetworkInterceptor(meter).build()
        fun get(path: String, body: String, chunked: Boolean) {
            server.enqueue(MockResponse().apply { if (chunked) setChunkedBody(body, 4) else setBody(body) })
            client.newCall(Request.Builder().url(server.url(path)).build()).execute().use { it.body.string() }
        }
        try {
            get("/g/catalog.json?board=g", "x".repeat(30), chunked = true)
            assertEquals(emptyList<Pair<String?, Long>>(), batches)
            get("/g/1.json?board=g", "x".repeat(30), chunked = false)
            assertEquals(listOf("g" to 60L), batches)
            get("/thread/?board=a", "x".repeat(50), chunked = true)
            get("/robots.txt", "tail", chunked = false)
            assertEquals(listOf("g" to 60L), batches)
            meter.flush()
            assertEquals(mapOf("g" to 60L, "a" to 50L, null to 4L), batches.toMap())
        } finally {
            server.shutdown()
        }
    }
}

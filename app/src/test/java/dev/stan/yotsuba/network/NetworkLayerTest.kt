package dev.stan.yotsuba.network

import dev.stan.yotsuba.core.network.CachePolicyInterceptor
import dev.stan.yotsuba.core.network.FourChanApi
import dev.stan.yotsuba.core.network.RateLimitInterceptor
import dev.stan.yotsuba.core.network.StaleIfOfflineInterceptor
import dev.stan.yotsuba.core.util.Urls
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class NetworkLayerTest {
    private lateinit var server: MockWebServer
    private lateinit var cacheDir: File
    private var offline = false

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        cacheDir = File.createTempFile("cache", null).apply { delete(); mkdirs() }
        offline = false
    }

    @After fun tearDown() {
        server.shutdown()
        cacheDir.deleteRecursively()
    }

    private fun client(rateLimitHost: String? = null): OkHttpClient {
        val b = OkHttpClient.Builder()
            .cache(Cache(cacheDir, 10L * 1024 * 1024))
            .addInterceptor(StaleIfOfflineInterceptor { offline })
            .addNetworkInterceptor(CachePolicyInterceptor())
            .readTimeout(2, TimeUnit.SECONDS)
        if (rateLimitHost != null) b.addInterceptor(RateLimitInterceptor(host = rateLimitHost, minIntervalMs = 200))
        return b.build()
    }

    private fun api(client: OkHttpClient): FourChanApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(client)
        .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(FourChanApi::class.java)

    @Test fun `success parses`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"boards":[{"board":"g","title":"Technology"}]}"""))
        val boards = api(client()).boards()
        assertEquals("g", boards.boards.single().board)
    }

    @Test fun `404 surfaces as HttpException 404 (dead thread)`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("404 Not Found"))
        try {
            api(client()).thread("g", 1)
            error("expected failure")
        } catch (e: HttpException) {
            assertEquals(404, e.code())
        }
    }

    @Test fun `500 surfaces as HttpException 500`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        try {
            api(client()).boards()
            error("expected failure")
        } catch (e: HttpException) {
            assertEquals(500, e.code())
        }
    }

    @Test fun `cache policy rewrites origin max-age=5 for boards`() {
        server.enqueue(
            MockResponse().setBody("""{"boards":[]}""")
                .setHeader("Cache-Control", "max-age=5, stale-while-revalidate=10")
        )
        val c = client()
        val url = server.url("/boards.json")
        c.newCall(Request.Builder().url(url).build()).execute().use { r ->
            assertEquals("public, max-age=86400", r.header("Cache-Control"))
        }
        // Second call within the imposed lifetime is served from cache, with no second request.
        c.newCall(Request.Builder().url(url).build()).execute().use { }
        assertEquals(1, server.requestCount)
    }

    @Test fun `thread responses always revalidate`() {
        server.enqueue(
            MockResponse().setBody("""{"posts":[]}""")
                .setHeader("Cache-Control", "max-age=5")
                .setHeader("Last-Modified", "Wed, 21 Oct 2015 07:28:00 GMT")
        )
        server.enqueue(MockResponse().setResponseCode(304))
        val c = client()
        val url = server.url("/g/thread/1.json")
        c.newCall(Request.Builder().url(url).build()).execute().use { r ->
            assertEquals("no-cache", r.header("Cache-Control"))
        }
        c.newCall(Request.Builder().url(url).build()).execute().use { r ->
            assertEquals(200, r.code) // served from cache after 304 revalidation
        }
        assertEquals(2, server.requestCount)
        assertTrue(server.takeRequest().headers["If-Modified-Since"] == null)
        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", server.takeRequest().headers["If-Modified-Since"])
    }

    @Test fun `stale-if-offline serves cached content when offline`() {
        server.enqueue(
            MockResponse().setBody("""{"posts":[{"no":1,"time":1}]}""")
                .setHeader("Cache-Control", "max-age=5")
        )
        val c = client()
        val url = server.url("/g/catalog.json")
        c.newCall(Request.Builder().url(url).build()).execute().use { }
        offline = true
        // Age out the 10 s catalog lifetime is irrelevant offline: only-if-cached + max-stale.
        c.newCall(Request.Builder().url(url).build()).execute().use { r ->
            assertEquals(200, r.code)
            assertTrue(r.body.string().contains("\"no\":1"))
        }
        assertEquals(1, server.requestCount)
    }

    @Test fun `rate limiter spaces requests to the API host`() {
        repeat(3) { server.enqueue(MockResponse().setBody("{}")) }
        val host = server.url("/").host
        val c = client(rateLimitHost = host)
        val start = System.nanoTime()
        repeat(3) {
            c.newCall(Request.Builder().url(server.url("/boards.json")).header("Cache-Control", "no-cache").build())
                .execute().use { }
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("expected >=400ms spacing, got $elapsedMs", elapsedMs >= 400)
    }
}

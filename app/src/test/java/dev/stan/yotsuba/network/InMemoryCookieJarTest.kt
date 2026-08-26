package dev.stan.yotsuba.network

import dev.stan.yotsuba.core.network.InMemoryCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryCookieJarTest {

    private val apiUrl = "https://a.4cdn.org/g/catalog.json".toHttpUrl()
    private val imageUrl = "https://i.4cdn.org/g/123.png".toHttpUrl()

    private fun cookie(name: String, value: String, host: String, expiresAt: Long) =
        Cookie.Builder().name(name).value(value).domain(host).expiresAt(expiresAt).build()

    private val farFuture = System.currentTimeMillis() + 3_600_000L

    @Test fun `round-trips a cookie for the same host`() {
        val jar = InMemoryCookieJar()
        jar.saveFromResponse(apiUrl, listOf(cookie("__cf_bm", "abc", "a.4cdn.org", farFuture)))
        assertEquals(listOf("abc"), jar.loadForRequest(apiUrl).map { it.value })
    }

    @Test fun `cookies are partitioned per host`() {
        val jar = InMemoryCookieJar()
        jar.saveFromResponse(apiUrl, listOf(cookie("__cf_bm", "abc", "a.4cdn.org", farFuture)))
        assertTrue(jar.loadForRequest(imageUrl).isEmpty())
    }

    @Test fun `a re-set cookie replaces its predecessor instead of piling up`() {
        val jar = InMemoryCookieJar()
        jar.saveFromResponse(apiUrl, listOf(cookie("__cf_bm", "old", "a.4cdn.org", farFuture)))
        jar.saveFromResponse(apiUrl, listOf(cookie("__cf_bm", "new", "a.4cdn.org", farFuture)))
        assertEquals(listOf("new"), jar.loadForRequest(apiUrl).map { it.value })
    }

    @Test fun `expired cookies are never sent`() {
        val jar = InMemoryCookieJar()
        jar.saveFromResponse(
            apiUrl,
            listOf(
                cookie("stale", "x", "a.4cdn.org", System.currentTimeMillis() - 1),
                cookie("fresh", "y", "a.4cdn.org", farFuture),
            ),
        )
        assertEquals(listOf("fresh"), jar.loadForRequest(apiUrl).map { it.name })
    }
}

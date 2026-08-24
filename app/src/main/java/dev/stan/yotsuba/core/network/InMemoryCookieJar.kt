package dev.stan.yotsuba.core.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Cloudflare sets `__cf_bm` on every response and OkHttp drops it by default; round-tripping it
 * within a session keeps the client looking like a normal consumer. Never persisted (D8).
 */
class InMemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, List<Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store[url.host] = (store[url.host].orEmpty().filter { old ->
            cookies.none { it.name == old.name }
        } + cookies)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store[url.host].orEmpty().filter { it.expiresAt > now && it.matches(url) }
    }
}

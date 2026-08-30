package dev.stan.yotsuba.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Identifies the app to the 4chan API and boards hosts, as their API rules ask, in place of
 * OkHttp's stock `okhttp/x.y.z`. Only for [hosts]: the same client fetches images and
 * archive pages, and those get whatever the caller set.
 */
class UserAgentInterceptor(
    private val userAgent: String,
    private val hosts: Set<String> = setOf("a.4cdn.org", "a.4chan.org"),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host !in hosts) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("User-Agent", userAgent).build())
    }

    companion object {
        /** `Yotsuba/<version> (+<repo url>)`, so an operator reading logs can find the source. */
        fun forApp(versionName: String, repo: String): String = "Yotsuba/$versionName (+https://github.com/$repo)"
    }
}

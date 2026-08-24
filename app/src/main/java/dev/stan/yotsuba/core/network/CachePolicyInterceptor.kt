package dev.stan.yotsuba.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * The origin sends `max-age=5, stale-while-revalidate=10` for everything, which grants none of
 * the lifetimes §5 wants, so the response Cache-Control is rewritten per endpoint (D8).
 * These numbers are ours, not the server's.
 */
class CachePolicyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val path = chain.request().url.encodedPath
        val policy = when {
            path.endsWith("/boards.json") -> "public, max-age=86400"
            path.endsWith("/catalog.json") -> "public, max-age=10"
            path.contains("/thread/") -> "no-cache"
            else -> return response
        }
        return response.newBuilder()
            .removeHeader("Pragma")
            .header("Cache-Control", policy)
            .build()
    }
}

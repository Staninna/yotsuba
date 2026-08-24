package dev.stan.yotsuba.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Serialises requests to the API host at >= minIntervalMs apart (§8).
 * Image hosts are deliberately not throttled.
 */
class RateLimitInterceptor(
    private val host: String = "a.4cdn.org",
    private val minIntervalMs: Long = 1_000,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleeper: (Long) -> Unit = Thread::sleep,
) : Interceptor {
    private val lock = Object()
    private var nextAllowedAt = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain.request().url.host == host) {
            // Reserve a slot under the lock, sleep outside it so concurrent
            // requests wait in parallel instead of pinning dispatcher threads.
            val wait = synchronized(lock) {
                val now = clock()
                val wait = nextAllowedAt - now
                nextAllowedAt = maxOf(now, nextAllowedAt) + minIntervalMs
                wait
            }
            if (wait > 0) sleeper(wait)
        }
        return chain.proceed(chain.request())
    }
}

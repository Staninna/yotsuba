package dev.stan.yotsuba.core.network

import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** Offline, serve whatever the cache holds regardless of age (§8). */
class StaleIfOfflineInterceptor(
    private val isOffline: () -> Boolean,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if (isOffline()) {
            request = request.newBuilder()
                .cacheControl(
                    CacheControl.Builder()
                        .onlyIfCached()
                        .maxStale(365, TimeUnit.DAYS)
                        .build()
                )
                .build()
        }
        return chain.proceed(request)
    }
}

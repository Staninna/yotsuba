package dev.stan.yotsuba.core.media

import android.content.Context
import coil3.SingletonImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Streams media bytes from Coil's disk cache when the viewer already fetched them,
 * otherwise from the network through the shared OkHttp client (timeouts, interceptors,
 * rate limiting included). Never buffers the whole file in memory.
 */
@Singleton
class MediaByteSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    /** Copies the media at [url] into [out]; [out] is not closed. Throws on failure. */
    fun copyTo(url: String, out: OutputStream) {
        val snapshot = SingletonImageLoader.get(context).diskCache?.openSnapshot(url)
        if (snapshot != null) {
            snapshot.use { s -> s.data.toFile().inputStream().use { it.copyTo(out) } }
            return
        }
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            response.body.byteStream().use { it.copyTo(out) }
        }
    }
}

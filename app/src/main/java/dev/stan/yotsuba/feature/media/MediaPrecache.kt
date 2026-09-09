package dev.stan.yotsuba.feature.media

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import dev.stan.yotsuba.core.network.NetworkStatus
import dev.stan.yotsuba.domain.model.Settings
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/**
 * Fetching the pages just past the open one before the user swipes to them, into the
 * caches the pages themselves read from: Coil's disk cache for an image, [VideoCache]
 * for a video. A page that plays from the vault has nothing to fetch.
 */

/** The indices to fetch ahead of [page]: the next [count] that exist, none when [count] is 0. */
internal fun precacheWindow(page: Int, count: Int, pageCount: Int): IntRange =
    (page + 1)..minOf(page + count, pageCount - 1)

/** How many pages ahead [settings] allow on [status]; 0 holds everything back. */
internal fun precacheAllowance(settings: Settings, status: NetworkStatus): Int = when {
    settings.dataSaver || status == NetworkStatus.Offline -> 0
    settings.precacheUnmeteredOnly && status != NetworkStatus.Unmetered -> 0
    else -> settings.precacheCount
}

/** Fetches in flight for a window of pages; retargeting drops whatever fell out of it. */
internal class Precacher(private val context: Context, private val scope: CoroutineScope) {
    private val inFlight = mutableMapOf<String, Job>()

    fun retarget(pages: List<ViewerPage>) {
        val wanted = pages.mapNotNull { p -> p.remoteUrl?.let { it to p.isVideo } }.toMap()
        inFlight.keys.filter { it !in wanted }.forEach { inFlight.remove(it)?.cancel() }
        // A finished job stays in the map, so a page is fetched once per stay in the window.
        wanted.forEach { (url, video) ->
            inFlight.getOrPut(url) { scope.launch { if (video) fetchVideo(url) else fetchImage(url) } }
        }
    }

    private suspend fun fetchImage(url: String) {
        // The disk cache key is the URL, the same one the page requests. A 1 px decode is
        // the cheapest way through Coil that still writes the bytes to disk, and nothing
        // goes into the memory cache.
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(1)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        SingletonImageLoader.get(context).execute(request)
    }

    private suspend fun fetchVideo(url: String) {
        try {
            // CacheWriter stops at a thread interrupt, which is how the cancel reaches it.
            runInterruptible(Dispatchers.IO) { VideoCache.writer(context, url).cache() }
        } catch (_: IOException) {
            // The page fetches it again when it gets there.
        }
    }
}

/** A [Precacher] whose fetches end with the composition. */
@Composable
internal fun rememberPrecacher(): Precacher {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember(scope) { Precacher(context, scope) }
}

/**
 * The one disk cache every video plays through, so a clip fetched ahead, or watched once,
 * comes off disk. media3 allows one [SimpleCache] per directory per process, hence the
 * process-wide object rather than a per-player instance.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal object VideoCache {
    private const val MAX_BYTES = 256L * 1024 * 1024
    private var instance: SimpleCache? = null

    @Synchronized
    private fun cache(context: Context): SimpleCache = instance ?: SimpleCache(
        File(context.cacheDir, "video_cache"),
        LeastRecentlyUsedCacheEvictor(MAX_BYTES),
        StandaloneDatabaseProvider(context.applicationContext),
    ).also { instance = it }

    private fun cacheFactory(context: Context) = CacheDataSource.Factory()
        .setCache(cache(context))
        .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** What a player reads through: a `file://` URI straight, http through the cache. */
    fun playbackFactory(context: Context): DataSource.Factory =
        DefaultDataSource.Factory(context, cacheFactory(context))

    /** Pulls the whole of [url] into the cache; `cache()` blocks until done or interrupted. */
    fun writer(context: Context, url: String): CacheWriter =
        CacheWriter(cacheFactory(context).createDataSource(), DataSpec(Uri.parse(url)), null, null)
}

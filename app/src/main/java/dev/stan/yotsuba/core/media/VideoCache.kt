package dev.stan.yotsuba.core.media

import android.content.Context
import android.net.Uri
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
import java.io.File

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

    /** Pulls the whole of [url] into the cache; blocks until done, failed or interrupted. */
    fun fetch(context: Context, url: String) {
        CacheWriter(cacheFactory(context).createDataSource(), DataSpec(Uri.parse(url)), null, null).cache()
    }

    /**
     * Drops everything cached, through the cache itself rather than off the directory:
     * media3 keeps an index beside the files, and deleting one without the other leaves a
     * cache that lies about what it holds. Safe while a video plays. A span being read is
     * unlinked with the reader's handle still open, and a span being written is locked, so
     * it survives the clear and the index stays true either way.
     */
    @Synchronized
    fun clear(context: Context) {
        val cache = cache(context)
        cache.keys.toList().forEach(cache::removeResource)
    }
}

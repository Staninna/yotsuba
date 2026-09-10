package dev.stan.yotsuba.data

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import dev.stan.yotsuba.data.repository.MaintenanceRepositoryImpl
import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClearCachesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The 256 MB video cache is the biggest thing the app writes; Clear cache must reach it. */
    @Test
    fun `clearCaches empties the video cache`() = runTest {
        val dir = File(context.cacheDir, "video_cache")
        val cache = SimpleCache(dir, NoOpCacheEvictor(), StandaloneDatabaseProvider(context))
        cache.startReadWrite("https://example.invalid/a.webm", 0, 4)
        val file = cache.startFile("https://example.invalid/a.webm", 0, 4)
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        cache.commitFile(file, 4)
        assertTrue(cache.cacheSpace > 0)
        // One SimpleCache per directory per process, so hand the directory over.
        cache.release()

        MaintenanceRepositoryImpl(context, OkHttpClient()).clearCaches()

        assertFalse(dir.walkTopDown().any { it.isFile && it.name.endsWith(".exo") })
    }
}

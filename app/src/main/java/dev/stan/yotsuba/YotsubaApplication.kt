package dev.stan.yotsuba

import android.app.Application
import android.os.StrictMode
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.HiltAndroidApp
import dev.stan.yotsuba.core.lock.AppLock
import dev.stan.yotsuba.core.work.PeriodicWorkScheduler
import dev.stan.yotsuba.domain.repository.BackupRepository
import javax.inject.Inject
import okhttp3.OkHttpClient

@HiltAndroidApp
class YotsubaApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var okHttpClient: OkHttpClient

    /** Injected only so the singleton exists from process start and its auto-export observer runs. */
    @Inject lateinit var backupRepository: BackupRepository

    @Inject lateinit var periodicWork: PeriodicWorkScheduler

    @Inject lateinit var appLock: AppLock

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Dev builds log any disk or network access on the main thread; the release
            // build never carries this.
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork()
                    .detectCustomSlowCalls().penaltyLog().build(),
            )
        }
        periodicWork.ensureScheduled()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLock)
    }

    /**
     * Coil shares the app's OkHttp client (pool, dispatcher, cookie jar) but keeps its own
     * ~200 MB diskCache, deliberately not the client's 10 MB JSON cache (D8/D12).
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                // ImageDecoder-backed animation is API 28+; 26 and 27 get the
                // pure-Kotlin GIF decoder instead of a NoClassDefFoundError.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .diskCache(
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            )
            .build()
}

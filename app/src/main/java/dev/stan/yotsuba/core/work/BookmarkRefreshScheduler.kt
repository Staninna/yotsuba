package dev.stan.yotsuba.core.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Registers the periodic bookmark refresher. An interface so the repository stays JVM-testable. */
interface BookmarkRefreshScheduler {
    fun ensureScheduled()
}

@Singleton
class WorkManagerBookmarkRefreshScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : BookmarkRefreshScheduler {

    override fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<BookmarkRefreshWorker>(
            BookmarkRefreshWorker.DEFAULT_INTERVAL_MINUTES, TimeUnit.MINUTES,
        )
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BookmarkRefreshWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {
    @Binds abstract fun bookmarkRefreshScheduler(impl: WorkManagerBookmarkRefreshScheduler): BookmarkRefreshScheduler
}

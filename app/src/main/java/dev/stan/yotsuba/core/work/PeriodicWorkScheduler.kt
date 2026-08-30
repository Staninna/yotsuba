package dev.stan.yotsuba.core.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.stan.yotsuba.core.di.ApplicationScope
import dev.stan.yotsuba.domain.repository.SettingsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Registers every periodic worker. An interface so the Application stays JVM-testable. */
interface PeriodicWorkScheduler {
    fun ensureScheduled()
}

@Singleton
class WorkManagerPeriodicWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : PeriodicWorkScheduler {

    override fun ensureScheduled() {
        // The App Startup provider initialises WorkManager before Application.onCreate on a
        // device; under Robolectric nothing does, and scheduling is not what those tests check.
        if (!WorkManager.isInitialized()) return
        val manager = WorkManager.getInstance(context)
        // The bookmark cadence follows Settings > Reading. UPDATE re-times the existing work
        // whenever the chip changes (and on every launch) without changing its identity.
        scope.launch {
            settings.settings.map { it.bookmarkRefreshMinutes }.distinctUntilChanged().collect { minutes ->
                manager.enqueueUniquePeriodicWork(
                    BookmarkRefreshWorker.UNIQUE_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    connectedPeriodic<BookmarkRefreshWorker>(minutes.toLong(), TimeUnit.MINUTES),
                )
            }
        }
        // KEEP: a no-op once the work exists.
        manager.enqueueUniquePeriodicWork(
            VaultSyncWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            connectedPeriodic<VaultSyncWorker>(VaultSyncWorker.INTERVAL_HOURS, TimeUnit.HOURS),
        )
    }

    private inline fun <reified W : ListenableWorker> connectedPeriodic(interval: Long, unit: TimeUnit): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<W>(interval, unit)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {
    @Binds abstract fun periodicWorkScheduler(impl: WorkManagerPeriodicWorkScheduler): PeriodicWorkScheduler
}

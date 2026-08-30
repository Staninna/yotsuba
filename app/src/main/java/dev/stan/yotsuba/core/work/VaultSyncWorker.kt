package dev.stan.yotsuba.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.stan.yotsuba.core.network.NetworkMonitor
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Periodic pass that keeps the vault's sidecars current: every live bookmark is snapshotted
 * whole (when the setting allows), then every other saved thread gets the manual Sync
 * treatment. Same entry-point pattern as [BookmarkRefreshWorker], for the same reason.
 *
 * The RateLimitInterceptor paces every thread fetch, and a rate-limited answer ends the
 * pass early rather than pushing through it; the next run picks up where it can.
 */
class VaultSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun mediaVaultRepository(): MediaVaultRepository
        fun bookmarkRepository(): BookmarkRepository
        fun settingsRepository(): SettingsRepository
        fun networkMonitor(): NetworkMonitor
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val vault = deps.mediaVaultRepository()
        if (!vault.hasStorageAccess()) return Result.success()
        val settings = deps.settingsRepository().settings.first()
        // The enqueue-time constraint is CONNECTED (a KEEP'd periodic request never changes);
        // data saver is honoured here, where the current setting is known. Success, not retry:
        // a backed-off re-run would only re-read the same setting and network, and the next
        // period is the next chance anyway.
        if (settings.dataSaver && deps.networkMonitor().current().isMetered) return Result.success()

        var snapshotted = emptySet<VaultLocation>()
        if (settings.snapshotWatchedThreads) {
            val live = deps.bookmarkRepository().bookmarks.first()
                .filter { it.isLive }
                .map { VaultLocation(it.board, it.threadNo) }
            val summary = vault.snapshotThreads(live)
            if (summary.rateLimited) return Result.retry()
            // What the snapshot pass settled, not what it was asked: a thread whose fetch
            // failed gets the sync pass below instead of waiting for the next run.
            snapshotted = summary.touched
        }
        val summary = vault.syncSavedThreads({ _, _ -> }, skip = snapshotted)
        return if (summary.rateLimited) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "vault-sync"

        /** Fixed cadence; a sidecar going a few hours stale costs nothing, a 404 in between is rare. */
        const val INTERVAL_HOURS = 6L
    }
}

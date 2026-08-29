package dev.stan.yotsuba.core.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.stan.yotsuba.R
import dev.stan.yotsuba.domain.repository.BookmarkRepository

/**
 * Periodic background pass over the live bookmarks: the same board-grouped refresh the tab
 * runs, followed by one notification when unread grew. Dependencies come through a Hilt
 * entry point rather than @HiltWorker, because installing HiltWorkerFactory needs the
 * Application class, which this feature doesn't own.
 */
class BookmarkRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun bookmarkRepository(): BookmarkRepository
    }

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).bookmarkRepository()
        // The RateLimitInterceptor on the shared OkHttp client paces every call this makes;
        // a pass over N boards takes at least N seconds and that's intended.
        val summary = repository.refreshAll()
        if (summary.newUnread > 0) notify(summary.newUnread, summary.threadsWithNew)
        return Result.success()
    }

    private fun notify(replies: Int, threads: Int) {
        val context = applicationContext
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.bookmarks_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val tap = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val res = context.resources
        val text = res.getQuantityString(R.plurals.bookmarks_notification_replies, replies, replies) +
            " " + res.getQuantityString(R.plurals.bookmarks_notification_threads, threads, threads)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.bookmarks_notification_title))
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        // POST_NOTIFICATIONS may be denied on 13+; the runtime prompt lives outside this feature.
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        const val UNIQUE_NAME = "bookmark-refresh"
        const val CHANNEL_ID = "bookmarks"
        const val NOTIFICATION_ID = 4001

        /** Until Settings grows a refresh-interval field, every install polls at this cadence. */
        const val DEFAULT_INTERVAL_MINUTES = 30L
    }
}

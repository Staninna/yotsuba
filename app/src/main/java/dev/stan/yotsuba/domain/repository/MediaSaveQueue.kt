package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.VaultSaveContext
import kotlinx.coroutines.flow.Flow

/**
 * The app-wide save queue as a screen sees it. Enqueueing returns at once; the work
 * happens in the background and shows up in [statuses].
 */
interface MediaSaveQueue {
    /**
     * Full CDN URL to its status, for every URL that is saved, waiting, in flight or
     * failed. A URL already in the vault reads [MediaSaveStatus.Saved] whatever the queue
     * thinks; anything else absent was never asked for.
     */
    val statuses: Flow<Map<String, MediaSaveStatus>>

    /** No-op while the same URL is already queued or downloading; a failed one goes again. */
    fun enqueue(item: MediaItem, context: VaultSaveContext)

    /** Removes a still-queued entry; a download already in flight cannot be cancelled. */
    fun cancel(url: String)

    /** Queues a failed entry again with the context it failed with. No-op for anything else. */
    fun retry(url: String)

    /** Clears a failed marker without retrying. */
    fun dismiss(url: String)
}

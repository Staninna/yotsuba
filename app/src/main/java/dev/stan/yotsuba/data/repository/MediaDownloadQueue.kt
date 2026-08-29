package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.MediaSaveQueue
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * App-wide save queue: enqueueing is instant, one background worker walks the queue
 * sequentially (the vault write streams from cache or network — no point fanning out
 * against the 1 s API courtesy). A successful save simply drops out of the queue's own
 * map; the vault's saved table takes over and [statuses] reads it as saved.
 */
@Singleton
class MediaDownloadQueue @Inject constructor(
    private val vault: MediaVaultRepository,
) : MediaSaveQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<Pair<MediaItem, VaultSaveContext>>(Channel.UNLIMITED)

    /** Queue-side state only: queued, downloading, failed. */
    private val queued = MutableStateFlow<Map<String, MediaSaveStatus>>(emptyMap())

    /** What a failed URL was asked to save, so [retry] can ask again. */
    private val failedRequests = MutableStateFlow<Map<String, Pair<MediaItem, VaultSaveContext>>>(emptyMap())

    override val statuses: Flow<Map<String, MediaSaveStatus>> = combine(vault.saved(), queued) { saved, queue ->
        buildMap {
            putAll(queue)
            saved.keys.forEach { put(it, MediaSaveStatus.Saved) }
        }
    }

    init {
        scope.launch {
            for ((item, ctx) in channel) {
                // A cancelled entry was removed from the map — skip its stale channel element.
                if (queued.value[item.fullUrl] != MediaSaveStatus.Queued) continue
                queued.update { it + (item.fullUrl to MediaSaveStatus.Downloading) }
                val error = vault.save(item, ctx)
                if (error == null) {
                    queued.update { it - item.fullUrl }
                } else {
                    failedRequests.update { it + (item.fullUrl to (item to ctx)) }
                    queued.update { it + (item.fullUrl to MediaSaveStatus.Failed(error)) }
                }
            }
        }
    }

    override fun enqueue(item: MediaItem, context: VaultSaveContext) {
        if (queued.value[item.fullUrl]?.inProgress == true) return
        failedRequests.update { it - item.fullUrl }
        queued.update { it + (item.fullUrl to MediaSaveStatus.Queued) }
        channel.trySend(item to context)
    }

    override fun cancel(url: String) {
        queued.update { if (it[url] == MediaSaveStatus.Queued) it - url else it }
    }

    override fun retry(url: String) {
        val (item, ctx) = failedRequests.value[url] ?: return
        enqueue(item, ctx)
    }

    override fun dismiss(url: String) {
        failedRequests.update { it - url }
        queued.update { if (it[url] is MediaSaveStatus.Failed) it - url else it }
    }
}

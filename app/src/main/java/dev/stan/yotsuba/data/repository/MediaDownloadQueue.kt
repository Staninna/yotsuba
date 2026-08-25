package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DownloadState {
    data object Queued : DownloadState
    data object Downloading : DownloadState
    data class Failed(val error: VaultError) : DownloadState
}

/**
 * App-wide save queue: enqueueing is instant, one background worker walks the queue
 * sequentially (the vault write streams from cache or network — no point fanning out
 * against the 1 s API courtesy). Statuses drive the per-item download icons; successful
 * saves simply disappear from here because the saved-media table takes over.
 */
@Singleton
class MediaDownloadQueue @Inject constructor(
    private val vault: MediaVaultRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<Pair<MediaItem, VaultSaveContext>>(Channel.UNLIMITED)

    private val _statuses = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    /** Full CDN URL → current state; absent = not in the queue (saved or never requested). */
    val statuses: StateFlow<Map<String, DownloadState>> = _statuses

    init {
        scope.launch {
            for ((item, ctx) in channel) {
                // A cancelled entry was removed from the map — skip its stale channel element.
                if (_statuses.value[item.fullUrl] != DownloadState.Queued) continue
                _statuses.update { it + (item.fullUrl to DownloadState.Downloading) }
                val error = vault.save(item, ctx)
                _statuses.update {
                    if (error == null) it - item.fullUrl
                    else it + (item.fullUrl to DownloadState.Failed(error))
                }
            }
        }
    }

    /** No-op while the same URL is already queued or downloading; a FAILED entry retries. */
    fun enqueue(item: MediaItem, context: VaultSaveContext) {
        val current = _statuses.value[item.fullUrl]
        if (current == DownloadState.Queued || current == DownloadState.Downloading) return
        _statuses.update { it + (item.fullUrl to DownloadState.Queued) }
        channel.trySend(item to context)
    }

    /** Removes a still-QUEUED entry; a download already in flight can't be cancelled. */
    fun cancel(url: String) {
        _statuses.update { if (it[url] == DownloadState.Queued) it - url else it }
    }

    /** Clears a FAILED marker without retrying. */
    fun dismissFailed(url: String) {
        _statuses.update { if (it[url] is DownloadState.Failed) it - url else it }
    }
}

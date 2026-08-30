package dev.stan.yotsuba.feature.media

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.stan.yotsuba.core.media.MediaByteSource
import dev.stan.yotsuba.core.network.NetworkMonitor
import dev.stan.yotsuba.core.network.NetworkStatus
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.MediaAutoplay
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.PostGraph
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.MediaSaveQueue
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the viewer has to show before (or instead of) media. */
sealed interface ViewerPhase {
    data object Loading : ViewerPhase
    /** The thread came back but no post carries media. */
    data object Empty : ViewerPhase
    /** Nothing live and nothing saved; [error] is what the network said. */
    data class Error(val error: NetworkError) : ViewerPhase
    data object Ready : ViewerPhase
}

data class MediaUiState(
    val phase: ViewerPhase = ViewerPhase.Loading,
    val items: List<MediaItem> = emptyList(),
    /** The conversation behind the media, live or rebuilt from the vault sidecar. */
    val thread: ViewerThread = ViewerThread(),
    /** URL → vault status: saved, waiting, in flight or failed; absent means never asked for. */
    val saveStatuses: Map<String, MediaSaveStatus> = emptyMap(),
    /**
     * URL → absolute path in the vault, or null for a legacy row whose file was never
     * located. Membership is "already saved"; a path plays the file from disk without
     * buffering. Empty while storage access is missing.
     */
    val saved: Map<String, String?> = emptyMap(),
    /** Whether the app may touch the vault directory right now. */
    val hasStorageAccess: Boolean = false,
    /** Whether a save also captures the post's conversation. */
    val saveReplies: Boolean = false,
    val initialIndex: Int = 0,
    val autoplay: Boolean = false,
    /** Data saver on a metered connection: no autoplay, full images wait for a tap. */
    val deferHeavyMedia: Boolean = false,
    val behaviour: ViewerBehaviour = ViewerBehaviour(),
    /** Unmuted by default only where the board declares webm_audio (D12). */
    val defaultUnmuted: Boolean = false,
) {
    /** The vault file for [url], when it is saved and its file is known. */
    fun savedPath(url: String): String? = saved[url]
}

@HiltViewModel(assistedFactory = MediaViewModel.Factory::class)
class MediaViewModel @AssistedInject constructor(
    @Assisted("board") private val board: String,
    @Assisted("threadNo") private val threadNo: Long,
    @Assisted("initialPostNo") private val initialPostNo: Long,
    @ApplicationContext private val appContext: Context,
    private val threadRepository: ThreadRepository,
    private val boardRepository: BoardRepository,
    private val settingsRepository: SettingsRepository,
    networkMonitor: NetworkMonitor,
    private val mediaVault: MediaVaultRepository,
    private val downloadQueue: MediaSaveQueue,
    private val byteSource: MediaByteSource,
    private val sessionStore: MediaSessionStore,
) : ViewModel() {

    /** The thread load, as far as it has got. */
    private sealed interface Source {
        data object Loading : Source
        data class Failed(val error: NetworkError) : Source
        data class Loaded(val details: ThreadDetails) : Source
    }

    private val source = MutableStateFlow<Source>(Source.Loading)
    private val boardInfo = MutableStateFlow<Board?>(null)
    /** Read directly for saves, which must not depend on whether the UI is collecting. */
    private val settingsState = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val loadedDetails: ThreadDetails?
        get() = (source.value as? Source.Loaded)?.details

    init {
        load()
    }

    /** Fetches the thread again after an error; a no-op while a load is already running. */
    fun retry() {
        if (source.value is Source.Failed) load()
    }

    private fun load() {
        source.value = Source.Loading
        viewModelScope.launch {
            // The board lookup can hit the network on a cold cache; it has nothing to
            // wait for, so it runs beside the thread fetch.
            val info = async { boardRepository.board(board) }
            // Live wins. The saved snapshot is the fallback for a pruned, 404'd or
            // offline thread, so a vault item still opens with its conversation intact.
            val r = threadRepository.thread(board, threadNo)
            val loaded = when (r) {
                is DataResult.Success -> r.value
                is DataResult.Failure -> mediaVault.savedThread(board, threadNo)
            }
            boardInfo.value = info.await()
            source.value = when {
                loaded != null -> Source.Loaded(loaded)
                r is DataResult.Failure -> Source.Failed(r.error)
                else -> Source.Failed(NetworkError.Unknown())
            }
        }
    }

    /** Persisted saves, storage access and the in-flight queue, grouped so the combine below stays at five flows. */
    private val saves = combine(
        mediaVault.saved(), mediaVault.storageAccess, downloadQueue.statuses,
    ) { saved, access, states -> Triple(saved, access, states) }

    val uiState: StateFlow<MediaUiState> = combine(
        source, boardInfo, settingsRepository.settings, networkMonitor.status, saves,
    ) { src, info, settings, status, (saved, access, states) ->
        val d = (src as? Source.Loaded)?.details
        val list = d?.posts.orEmpty().mapNotNull { it.presentMedia }
        val defer = defersHeavyMedia(settings.dataSaver, status)
        MediaUiState(
            phase = when (src) {
                Source.Loading -> ViewerPhase.Loading
                is Source.Failed -> ViewerPhase.Error(src.error)
                is Source.Loaded -> if (list.isEmpty()) ViewerPhase.Empty else ViewerPhase.Ready
            },
            items = list,
            thread = ViewerThread.of(d, info),
            saveStatuses = states,
            saved = if (access) saved else emptyMap(),
            hasStorageAccess = access,
            saveReplies = settings.saveRepliesWithMedia,
            initialIndex = list.indexOfFirst { it.postNo == initialPostNo }.coerceAtLeast(0),
            autoplay = !defer && when (settings.mediaAutoplay) {
                MediaAutoplay.ALWAYS -> true
                MediaAutoplay.NEVER -> false
                MediaAutoplay.UNMETERED_ONLY -> status == NetworkStatus.Unmetered
            },
            deferHeavyMedia = defer,
            behaviour = ViewerBehaviour(
                keepScreenOn = settings.keepScreenOnWhileWatching,
                doubleTapSeek = settings.doubleTapSeekEnabled,
                seekStepSeconds = settings.seekStep.seconds,
                holdToSave = settings.holdToSave,
            ),
            defaultUnmuted = info?.webmAudio == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaUiState())

    fun onMediaViewed(postNo: Long) = sessionStore.setLastViewed(board, threadNo, postNo)

    /** Re-reads the all-files grant; the screen calls this when it resumes. */
    fun refreshStorageAccess() = mediaVault.refreshStorageAccess()

    /** Queues a vault save with full thread/post context; returns immediately. */
    fun enqueueSave(item: MediaItem) {
        val loaded = loadedDetails
        val op = loaded?.posts?.firstOrNull { it.isOp }
        downloadQueue.enqueue(
            item,
            VaultSaveContext(
                board = board,
                threadNo = threadNo,
                threadSubject = op?.subject,
                opExcerpt = op?.body?.plainText?.takeIf { it.isNotBlank() },
                post = loaded?.posts?.firstOrNull { it.no == item.postNo },
                conversation = conversationFor(item.postNo, loaded),
            ),
        )
    }

    /**
     * The posts worth keeping beside [postNo]: everything it quotes and everything that
     * quotes it, transitively. Empty when the user has reply capture off.
     */
    private fun conversationFor(postNo: Long, loaded: ThreadDetails?): List<ThreadPost> =
        if (loaded == null || !settingsState.value.saveRepliesWithMedia) {
            emptyList()
        } else {
            PostGraph.of(loaded).conversationAround(postNo)
        }

    /** Deletes the saved file, its meta entry, and DB row. */
    fun removeDownload(url: String) {
        viewModelScope.launch { mediaVault.delete(url) }
    }

    /** Delete first, then queue a fresh save — sequenced so they can't race. */
    fun redownload(item: MediaItem) {
        viewModelScope.launch {
            mediaVault.delete(item.fullUrl)
            enqueueSave(item)
        }
    }

    fun cancelQueued(url: String) = downloadQueue.cancel(url)

    fun retryFailed(url: String) = downloadQueue.retry(url)

    fun dismissFailed(url: String) = downloadQueue.dismiss(url)

    /**
     * The file to hand to the share sheet: the vault copy when there is one, otherwise a
     * fresh download into the share cache. Null when it couldn't be fetched.
     */
    suspend fun prepareShare(item: MediaItem): File? = withContext(Dispatchers.IO) {
        uiState.value.savedPath(item.fullUrl)
            ?.let(::File)
            ?.takeIf { it.isFile }
            ?: runCatching {
                val dir = File(appContext.cacheDir, SHARE_CACHE_DIR).apply { mkdirs() }
                val file = File(dir, item.displayName)
                file.outputStream().use { byteSource.copyTo(item.fullUrl, it) }
                trimShareCache(dir, keep = file)
                file
            }.getOrNull()
    }

    /** Keeps the share cache to the newest [SHARE_CACHE_LIMIT] files; [keep] always survives. */
    private fun trimShareCache(dir: File, keep: File) {
        dir.listFiles { f -> f.isFile }
            ?.sortedByDescending { if (it == keep) Long.MAX_VALUE else it.lastModified() }
            ?.drop(SHARE_CACHE_LIMIT)
            ?.forEach { it.delete() }
    }

    private companion object {
        const val SHARE_CACHE_DIR = "shared_media"
        const val SHARE_CACHE_LIMIT = 20
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("board") board: String,
            @Assisted("threadNo") threadNo: Long,
            @Assisted("initialPostNo") initialPostNo: Long,
        ): MediaViewModel
    }
}

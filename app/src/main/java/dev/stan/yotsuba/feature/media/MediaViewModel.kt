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
import dev.stan.yotsuba.data.repository.DownloadState
import dev.stan.yotsuba.data.repository.MediaDownloadQueue
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.MediaAutoplay
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.PostGraph
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
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
    /** Full URLs the user has saved to the gallery. */
    val downloadedUrls: Set<String> = emptySet(),
    /** URL → queue state for saves in flight. */
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    /** URL → absolute path in the vault, for buffer-free playback from disk. */
    val savedPaths: Map<String, String> = emptyMap(),
    val initialIndex: Int = 0,
    val autoplay: Boolean = false,
    val behaviour: ViewerBehaviour = ViewerBehaviour(),
    /** Unmuted by default only where the board declares webm_audio (D12). */
    val defaultUnmuted: Boolean = false,
) {
    val loaded: Boolean get() = phase == ViewerPhase.Ready

    val posts: Map<Long, ThreadPost> get() = thread.posts
    val backlinks: Map<Long, List<Long>> get() = thread.backlinks
    val board: Board? get() = thread.board

    /** The thread's quote graph, for walking replies and the posts they answer. */
    val graph: PostGraph get() = thread.graph
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
    private val downloadQueue: MediaDownloadQueue,
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
    private val details = MutableStateFlow<ThreadDetails?>(null)
    private val boardInfo = MutableStateFlow<Board?>(null)

    /** OP-derived save context, computed once when the thread arrives. */
    private var saveContextBase: VaultSaveContext? = null

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
            // Live wins. The saved snapshot is the fallback for a pruned, 404'd or
            // offline thread, so a vault item still opens with its conversation intact.
            val r = threadRepository.thread(board, threadNo)
            val loaded = when (r) {
                is DataResult.Success -> r.value
                is DataResult.Failure -> mediaVault.savedThread(board, threadNo)
            }
            if (r is DataResult.Success) {
                val op = r.value.posts.firstOrNull { it.isOp }
                saveContextBase = VaultSaveContext(
                    board = board,
                    threadNo = threadNo,
                    threadSubject = op?.subject,
                    opExcerpt = op?.body?.plainText?.takeIf { it.isNotBlank() },
                    post = null,
                )
            }
            boardInfo.value = boardRepository.board(board)
            details.value = loaded
            source.value = when {
                loaded != null -> Source.Loaded(loaded)
                r is DataResult.Failure -> Source.Failed(r.error)
                else -> Source.Failed(NetworkError.Unknown())
            }
        }
    }

    /** Persisted saves + in-flight queue, merged so the combine below stays at five flows. */
    private data class SaveInfo(
        val downloaded: Set<String>,
        val states: Map<String, DownloadState>,
        val paths: Map<String, String>,
    )

    private val saveInfo = combine(
        mediaVault.savedUrls(), mediaVault.savedPaths(), downloadQueue.statuses,
    ) { urls, paths, states ->
        SaveInfo(downloaded = urls, states = states, paths = paths)
    }

    val uiState: StateFlow<MediaUiState> = combine(
        source, boardInfo, settingsRepository.settings, networkMonitor.status, saveInfo,
    ) { src, info, settings, status, saves ->
        val d = (src as? Source.Loaded)?.details
        val list = d?.posts.orEmpty().mapNotNull { it.presentMedia }
        MediaUiState(
            phase = when (src) {
                Source.Loading -> ViewerPhase.Loading
                is Source.Failed -> ViewerPhase.Error(src.error)
                is Source.Loaded -> if (list.isEmpty()) ViewerPhase.Empty else ViewerPhase.Ready
            },
            items = list,
            thread = ViewerThread.of(d, info),
            downloadedUrls = saves.downloaded,
            downloadStates = saves.states,
            savedPaths = if (mediaVault.hasStorageAccess()) saves.paths else emptyMap(),
            initialIndex = list.indexOfFirst { it.postNo == initialPostNo }.coerceAtLeast(0),
            autoplay = when (settings.mediaAutoplay) {
                MediaAutoplay.ALWAYS -> true
                MediaAutoplay.NEVER -> false
                MediaAutoplay.UNMETERED_ONLY -> status == NetworkStatus.Unmetered
            },
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

    fun hasStorageAccess(): Boolean = mediaVault.hasStorageAccess()

    private val settingsState = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    /** Queues a vault save with full thread/post context; returns immediately. */
    fun enqueueSave(item: MediaItem) {
        val base = saveContextBase ?: VaultSaveContext(board, threadNo, null, null, null)
        val loaded = details.value
        val post = loaded?.posts?.firstOrNull { it.no == item.postNo }
        downloadQueue.enqueue(
            item,
            base.copy(post = post, conversation = conversationFor(item.postNo, loaded)),
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

    fun dismissFailed(url: String) = downloadQueue.dismissFailed(url)

    /** Copies the media into the share cache; null when it couldn't be fetched. */
    suspend fun prepareShare(item: MediaItem): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(appContext.cacheDir, "shared_media").apply { mkdirs() }
            val file = File(dir, item.displayName)
            file.outputStream().use { byteSource.copyTo(item.fullUrl, it) }
            file
        }.getOrNull()
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

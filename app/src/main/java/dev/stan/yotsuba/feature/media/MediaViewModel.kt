package dev.stan.yotsuba.feature.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.core.database.dao.SavedMediaDao
import dev.stan.yotsuba.core.network.NetworkMonitor
import dev.stan.yotsuba.core.network.NetworkStatus
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.data.repository.DownloadState
import dev.stan.yotsuba.data.repository.MediaDownloadQueue
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.MediaAutoplay
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import dev.stan.yotsuba.domain.repository.VaultSaveContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MediaUiState(
    val items: List<MediaItem> = emptyList(),
    /** postNo -> post, for the sub-thread panel. */
    val posts: Map<Long, ThreadPost> = emptyMap(),
    /** postNo -> posts quoting it (D11). */
    val backlinks: Map<Long, List<Long>> = emptyMap(),
    /** Full URLs the user has saved to the gallery. */
    val downloadedUrls: Set<String> = emptySet(),
    /** URL → queue state for saves in flight. */
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    /** URL → absolute path in the vault, for buffer-free playback from disk. */
    val savedPaths: Map<String, String> = emptyMap(),
    val initialIndex: Int = 0,
    val board: Board? = null,
    val autoplay: Boolean = false,
    /** Unmuted by default only where the board declares webm_audio (D12). */
    val defaultUnmuted: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel(assistedFactory = MediaViewModel.Factory::class)
class MediaViewModel @AssistedInject constructor(
    @Assisted("board") private val board: String,
    @Assisted("threadNo") private val threadNo: Long,
    @Assisted("initialPostNo") private val initialPostNo: Long,
    private val threadRepository: ThreadRepository,
    private val boardRepository: BoardRepository,
    settingsRepository: SettingsRepository,
    networkMonitor: NetworkMonitor,
    private val savedMediaDao: SavedMediaDao,
    private val mediaVault: MediaVaultRepository,
    private val downloadQueue: MediaDownloadQueue,
    private val sessionStore: MediaSessionStore,
) : ViewModel() {

    fun onMediaViewed(postNo: Long) = sessionStore.setLastViewed(board, threadNo, postNo)

    fun needsStorageAccess(): Boolean = !mediaVault.hasStorageAccess()

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

    /** Queues a vault save with full thread/post context; returns immediately. */
    fun enqueueSave(item: MediaItem) {
        val posts = details.value?.posts.orEmpty()
        val op = posts.firstOrNull { it.isOp }
        downloadQueue.enqueue(
            item,
            VaultSaveContext(
                board = board,
                threadNo = threadNo,
                threadSubject = op?.subject,
                opExcerpt = op?.body?.plainText?.takeIf { it.isNotBlank() },
                post = posts.firstOrNull { it.no == item.postNo },
            ),
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("board") board: String,
            @Assisted("threadNo") threadNo: Long,
            @Assisted("initialPostNo") initialPostNo: Long,
        ): MediaViewModel
    }

    private val details = MutableStateFlow<dev.stan.yotsuba.domain.model.ThreadDetails?>(null)
    private val boardInfo = MutableStateFlow<Board?>(null)

    init {
        viewModelScope.launch {
            val r = threadRepository.thread(board, threadNo)
            if (r is DataResult.Success) details.value = r.value
            boardInfo.value = boardRepository.board(board)
        }
    }

    /** Persisted saves + in-flight queue, merged so the combine below stays at five flows. */
    private data class SaveInfo(
        val downloaded: Set<String>,
        val states: Map<String, DownloadState>,
        val paths: Map<String, String>,
    )

    private val saveInfo = combine(savedMediaDao.all(), downloadQueue.statuses) { entities, states ->
        SaveInfo(
            downloaded = entities.mapTo(mutableSetOf()) { it.url },
            states = states,
            paths = entities.filter { it.absolutePath.isNotEmpty() }
                .associate { it.url to it.absolutePath },
        )
    }

    val uiState: StateFlow<MediaUiState> = combine(
        details, boardInfo, settingsRepository.settings, networkMonitor.status, saveInfo,
    ) { d, info, settings, status, saves ->
        val list = d?.posts.orEmpty().mapNotNull { it.media }.filter { !it.deleted }
        MediaUiState(
            items = list,
            posts = d?.posts.orEmpty().associateBy { it.no },
            backlinks = d?.backlinks.orEmpty(),
            downloadedUrls = saves.downloaded,
            downloadStates = saves.states,
            savedPaths = if (mediaVault.hasStorageAccess()) saves.paths else emptyMap(),
            initialIndex = list.indexOfFirst { it.postNo == initialPostNo }.coerceAtLeast(0),
            board = info,
            autoplay = when (settings.mediaAutoplay) {
                MediaAutoplay.ALWAYS -> true
                MediaAutoplay.NEVER -> false
                MediaAutoplay.UNMETERED_ONLY -> status == NetworkStatus.Unmetered
            },
            defaultUnmuted = info?.webmAudio == true,
            loaded = list.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaUiState())
}

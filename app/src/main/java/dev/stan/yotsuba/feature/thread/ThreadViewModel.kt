package dev.stan.yotsuba.feature.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ThreadViewModel.Factory::class)
class ThreadViewModel @AssistedInject constructor(
    @Assisted("board") private val board: String,
    @Assisted private val threadNo: Long,
    private val threadRepository: ThreadRepository,
    private val boardRepository: BoardRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val mediaSessionStore: dev.stan.yotsuba.feature.media.MediaSessionStore,
    savedMediaDao: dev.stan.yotsuba.core.database.dao.SavedMediaDao,
    downloadQueue: dev.stan.yotsuba.data.repository.MediaDownloadQueue,
) : ViewModel() {

    /** URL → vault status for the thumbnail badges; saved wins over any queue state. */
    private val mediaSaveStatuses = combine(savedMediaDao.urls(), downloadQueue.statuses) { saved, queue ->
        buildMap {
            queue.forEach { (url, s) ->
                put(
                    url,
                    when (s) {
                        dev.stan.yotsuba.data.repository.DownloadState.QUEUED -> MediaSaveStatus.QUEUED
                        dev.stan.yotsuba.data.repository.DownloadState.DOWNLOADING -> MediaSaveStatus.DOWNLOADING
                        dev.stan.yotsuba.data.repository.DownloadState.FAILED -> MediaSaveStatus.FAILED
                    },
                )
            }
            saved.forEach { put(it, MediaSaveStatus.SAVED) }
        }
    }

    /** Last media item viewed in the full-screen viewer for this thread, cleared on read. */
    fun consumeLastViewedMedia(): Long? = mediaSessionStore.consumeLastViewed(board, threadNo)

    /** Reading position saved on scroll, restored when the thread is reopened. */
    suspend fun savedScrollPosition(): Long? = historyRepository.lastScrollPosition(board, threadNo)

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("board") board: String, threadNo: Long): ThreadViewModel
    }

    private val result = MutableStateFlow<DataResult<ThreadDetails>?>(null)
    private val boardInfo = MutableStateFlow<Board?>(null)
    private val revealedSpoilers = MutableStateFlow<Set<Pair<Long, Int>>>(emptySet())
    private val revealedImageSpoilers = MutableStateFlow<Set<Long>>(emptySet())
    private val newPostsAfter = MutableStateFlow<Pair<Long, Int>?>(null)
    private val archivedNotice = MutableStateFlow(false)
    private val searchQuery = MutableStateFlow<String?>(null)
    private val searchIndex = MutableStateFlow(0)
    private val previewStack = MutableStateFlow<List<List<Long>>>(emptyList())
    private val pendingExternalUrl = MutableStateFlow<String?>(null)
    private val autoRefreshUserOverride = MutableStateFlow<Boolean?>(null)

    private var pollJob: Job? = null
    private var lastKnownPostNo = 0L
    private var backoffIndex = 0
    private val backoffMs = listOf(10_000L, 30_000L, 60_000L, 300_000L)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val bookmarked = flowOf(Unit).flatMapLatest {
        bookmarkRepository.isBookmarked(board, threadNo)
    }

    init {
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!forceRefresh) result.value = null
            val r = threadRepository.thread(board, threadNo, forceRefresh)
            if (r is DataResult.Success) {
                val newest = r.value.posts.maxOfOrNull { it.no } ?: 0L
                if (lastKnownPostNo != 0L && newest > lastKnownPostNo) {
                    val newOnes = r.value.posts.count { it.no > lastKnownPostNo }
                    newPostsAfter.value = lastKnownPostNo to newOnes
                    backoffIndex = 0
                }
                lastKnownPostNo = newest
                recordHistory(r.value)
                // Viewing the thread clears its bookmark's unread badge (no-op if not bookmarked).
                if (newest > 0) {
                    bookmarkRepository.markSeen(board, threadNo, newest, r.value.posts.size - 1)
                }
            }
            if (r is DataResult.Failure && r.error == NetworkError.NotFound) {
                archivedNotice.value = true
                stopPolling()
                if (result.value is DataResult.Success) return@launch // keep showing content
            }
            result.value = r
        }
    }

    private suspend fun recordHistory(details: ThreadDetails) {
        val settings = settingsRepository.settings.first()
        if (!settings.recordHistory) return
        val op = details.posts.firstOrNull() ?: return
        historyRepository.record(
            HistoryEntry(
                board = board,
                threadNo = threadNo,
                subject = op.subject,
                opExcerpt = op.body.plainText.take(200),
                thumbnailUrl = op.media?.thumbnailUrl,
                viewedAt = System.currentTimeMillis(),
                lastScrollPostNo = historyRepository.lastScrollPosition(board, threadNo),
            )
        )
    }

    val uiState: StateFlow<ThreadUiState> = combine(
        result, boardInfo, bookmarked, settingsRepository.settings,
        revealedSpoilers, revealedImageSpoilers, newPostsAfter, archivedNotice,
        searchQuery, searchIndex, previewStack, pendingExternalUrl, autoRefreshUserOverride,
        mediaSaveStatuses,
    ) { values ->
        val res = values[0] as DataResult<*>?
        val info = values[1] as Board?
        val isBookmarked = values[2] as Boolean
        val settings = values[3] as dev.stan.yotsuba.domain.model.Settings
        @Suppress("UNCHECKED_CAST")
        val spoilers = values[4] as Set<Pair<Long, Int>>
        @Suppress("UNCHECKED_CAST")
        val imageSpoilers = values[5] as Set<Long>
        val newAfter = values[6] as Pair<Long, Int>?
        val archived = values[7] as Boolean
        val query = values[8] as String?
        val index = values[9] as Int
        @Suppress("UNCHECKED_CAST")
        val stack = values[10] as List<List<Long>>
        val pendingUrl = values[11] as String?
        val autoOverride = values[12] as Boolean?
        @Suppress("UNCHECKED_CAST")
        val saveStatuses = values[13] as Map<String, MediaSaveStatus>
        when (res) {
            null -> ThreadUiState.Loading
            is DataResult.Failure -> ThreadUiState.Error(res.error)
            is DataResult.Success -> {
                val details = res.value as ThreadDetails
                val matches = if (query.isNullOrBlank()) emptyList() else details.posts.filter {
                    it.body.plainText.contains(query, true) || it.subject?.contains(query, true) == true
                }.map { it.no }
                val byNo = details.posts.associateBy { it.no }
                ThreadUiState.Success(
                    details = details,
                    board = info,
                    bookmarked = isBookmarked,
                    revealAllSpoilers = settings.revealAllSpoilers,
                    revealedSpoilers = spoilers,
                    revealedImageSpoilers = imageSpoilers,
                    newPostsAfter = newAfter?.first,
                    newPostsCount = newAfter?.second ?: 0,
                    autoRefreshEnabled = autoOverride ?: settings.autoRefreshEnabled,
                    archivedNotice = archived || details.archived,
                    searchQuery = query,
                    searchMatches = matches,
                    searchIndex = if (matches.isEmpty()) 0 else index.coerceIn(0, matches.size - 1),
                    previewStack = stack.map { group -> group.mapNotNull { byNo[it] } }.filter { it.isNotEmpty() },
                    pendingExternalUrl = pendingUrl,
                    confirmBeforeOpeningLinks = settings.confirmBeforeOpeningLinks,
                    trustedDomains = settings.trustedDomains,
                    mediaSaveStatuses = saveStatuses,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThreadUiState.Loading)

    init {
        viewModelScope.launch { boardInfo.value = boardRepository.board(board) }
    }

    /** Poll only while the thread is on screen (D17); the screen drives visibility. */
    fun onScreenVisibilityChanged(visible: Boolean) {
        if (visible) maybeStartPolling() else stopPolling()
    }

    private fun maybeStartPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                val enabled = autoRefreshUserOverride.value
                    ?: settingsRepository.settings.first().autoRefreshEnabled
                if (!enabled || archivedNotice.value) { delay(5_000); continue }
                delay(backoffMs[backoffIndex])
                backoffIndex = (backoffIndex + 1).coerceAtMost(backoffMs.size - 1)
                load(forceRefresh = true)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun onToggleAutoRefresh() {
        val current = autoRefreshUserOverride.value
        viewModelScope.launch {
            val effective = current ?: settingsRepository.settings.first().autoRefreshEnabled
            autoRefreshUserOverride.value = !effective
            backoffIndex = 0
        }
    }

    fun onToggleBookmark() = viewModelScope.launch {
        val state = uiState.value as? ThreadUiState.Success ?: return@launch
        if (state.bookmarked) {
            bookmarkRepository.remove(board, threadNo)
        } else {
            val op = state.details.posts.firstOrNull() ?: return@launch
            bookmarkRepository.add(
                Bookmark(
                    board = board,
                    threadNo = threadNo,
                    subject = op.subject,
                    opExcerpt = op.body.plainText.take(200),
                    thumbnailUrl = op.media?.thumbnailUrl,
                    replyCount = state.details.posts.size - 1,
                    imageCount = state.details.posts.count { it.media != null },
                    bookmarkedAt = System.currentTimeMillis(),
                    lastCheckedAt = System.currentTimeMillis(),
                    lastSeenPostNo = state.details.posts.maxOfOrNull { it.no },
                    state = BookmarkState.ALIVE,
                )
            )
        }
    }

    fun onRevealSpoiler(postNo: Long, spoilerId: Int) {
        revealedSpoilers.value = revealedSpoilers.value + (postNo to spoilerId)
    }

    fun onRevealImageSpoiler(postNo: Long) {
        revealedImageSpoilers.value = revealedImageSpoilers.value + postNo
    }

    fun onOpenPreview(postNo: Long) {
        previewStack.value = previewStack.value + listOf(listOf(postNo))
    }

    fun onOpenBacklinks(postNo: Long) {
        val s = uiState.value as? ThreadUiState.Success ?: return
        val links = s.details.backlinks[postNo].orEmpty()
        if (links.isNotEmpty()) previewStack.value = previewStack.value + listOf(links)
    }

    fun onClosePreview() {
        previewStack.value = previewStack.value.dropLast(1)
    }

    fun onSearchChange(query: String?) {
        searchQuery.value = query
        searchIndex.value = 0
    }

    fun onSearchStep(delta: Int) {
        val s = uiState.value as? ThreadUiState.Success ?: return
        if (s.searchMatches.isEmpty()) return
        searchIndex.value = (searchIndex.value + delta).mod(s.searchMatches.size)
    }

    /** External-link tap: trusted domains skip the dialog (D26). */
    fun onExternalLink(url: String): Boolean {
        val s = uiState.value as? ThreadUiState.Success ?: return false
        val domain = Urls.domainOf(url)
        return if (!s.confirmBeforeOpeningLinks || (domain != null && domain in s.trustedDomains)) {
            true // open immediately
        } else {
            pendingExternalUrl.value = url
            false
        }
    }

    fun onDismissLinkDialog() { pendingExternalUrl.value = null }

    fun onTrustDomain(url: String) = viewModelScope.launch {
        val domain = Urls.domainOf(url) ?: return@launch
        settingsRepository.update { it.copy(trustedDomains = it.trustedDomains + domain) }
        pendingExternalUrl.value = null
    }

    fun onScrolledTo(postNo: Long) = viewModelScope.launch {
        historyRepository.updateScrollPosition(board, threadNo, postNo)
    }

    /**
     * The bottom-most post currently on screen: raises the "read up to" high-water mark and
     * live-updates the bookmark's unread pill ([unreadRemaining] = loaded posts below it),
     * so counts are correct the moment the user returns to the bookmarks tab.
     */
    fun onReadUpTo(postNo: Long, unreadRemaining: Int) = viewModelScope.launch {
        // Scrolling back up must not inflate the count — the mark only ever rises.
        val current = historyRepository.readUpTo(board, threadNo)
        if (current != null && postNo < current) return@launch
        historyRepository.updateReadUpTo(board, threadNo, postNo)
        bookmarkRepository.updateUnread(board, threadNo, unreadRemaining)
    }

    fun onDismissNewPostsDivider() { newPostsAfter.value = null }
}

package dev.stan.yotsuba.feature.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.data.repository.DownloadState
import dev.stan.yotsuba.data.repository.MediaDownloadQueue
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import dev.stan.yotsuba.feature.media.MediaSessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ThreadViewModel.Factory::class)
class ThreadViewModel @AssistedInject constructor(
    @Assisted("board") private val board: String,
    @Assisted("threadNo") private val threadNo: Long,
    @Assisted("initialPostNo") private val initialPostNo: Long?,
    private val threadRepository: ThreadRepository,
    private val boardRepository: BoardRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val mediaSessionStore: MediaSessionStore,
    mediaVault: MediaVaultRepository,
    downloadQueue: MediaDownloadQueue,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("board") board: String,
            @Assisted("threadNo") threadNo: Long,
            @Assisted("initialPostNo") initialPostNo: Long?,
        ): ThreadViewModel
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

    private val scrollTargetFlow = MutableStateFlow<ScrollTarget?>(null)
    private val topVisiblePostNo = MutableStateFlow<Long?>(null)
    private val bottomVisiblePostNo = MutableStateFlow<Long?>(null)

    private var lastKnownPostNo = 0L
    private var restoredScroll = false

    private val poller = ThreadPoller(
        isEnabled = {
            !archivedNotice.value &&
                (autoRefreshUserOverride.value ?: settingsRepository.settings.first().autoRefreshEnabled)
        },
        poll = { load(forceRefresh = true) },
    )

    private val bookmarked = bookmarkRepository.isBookmarked(board, threadNo)

    private val settingsState = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    /** URL → vault status for the thumbnail badges; saved wins over any queue state. */
    private val mediaSaveStatuses = combine(mediaVault.savedUrls(), downloadQueue.statuses) { saved, queue ->
        buildMap {
            queue.forEach { (url, s) ->
                put(
                    url,
                    when (s) {
                        is DownloadState.Queued -> MediaSaveStatus.QUEUED
                        is DownloadState.Downloading -> MediaSaveStatus.DOWNLOADING
                        is DownloadState.Failed -> MediaSaveStatus.FAILED
                    },
                )
            }
            saved.forEach { put(it, MediaSaveStatus.SAVED) }
        }
    }

    private val spoilerState = combine(revealedSpoilers, revealedImageSpoilers, ::SpoilerState)
    private val searchInput = combine(searchQuery, searchIndex, ::SearchInput)
    private val overlayState = combine(previewStack, pendingExternalUrl, ::OverlayState)
    private val refreshState = combine(newPostsAfter, archivedNotice, autoRefreshUserOverride, ::RefreshState)
    private val metaState = combine(boardInfo, bookmarked, mediaSaveStatuses, ::MetaState)
    private val sessionState = combine(spoilerState, searchInput, overlayState, refreshState, ::SessionState)

    val uiState: StateFlow<UiState<ThreadContent>> = combine(
        result, settingsRepository.settings, metaState, sessionState,
    ) { res, settings, meta, session ->
        when (res) {
            null -> UiState.Loading
            is DataResult.Failure -> UiState.Error(res.error)
            is DataResult.Success -> {
                val details = res.value
                val matches = searchMatches(details.posts, session.search.query)
                val byNo = details.posts.associateBy { it.no }
                UiState.Success(
                    ThreadContent(
                        details = details,
                        board = meta.board,
                        bookmarked = meta.bookmarked,
                        revealAllSpoilers = settings.revealAllSpoilers,
                        revealedSpoilers = session.spoilers.revealedText,
                        revealedImageSpoilers = session.spoilers.revealedImages,
                        newPostsAfter = session.refresh.newPostsAfter?.first,
                        newPostsCount = session.refresh.newPostsAfter?.second ?: 0,
                        autoRefreshEnabled = session.refresh.autoRefreshOverride ?: settings.autoRefreshEnabled,
                        archivedNotice = session.refresh.archived || details.archived,
                        searchQuery = session.search.query,
                        searchMatches = matches,
                        searchIndex = if (matches.isEmpty()) 0 else session.search.index.coerceIn(0, matches.size - 1),
                        previewStack = session.overlays.previewPostNos
                            .map { group -> group.mapNotNull { byNo[it] } }
                            .filter { it.isNotEmpty() },
                        pendingExternalUrl = session.overlays.pendingExternalUrl,
                        confirmBeforeOpeningLinks = settings.confirmBeforeOpeningLinks,
                        trustedDomains = settings.trustedDomains,
                        mediaSaveStatuses = meta.mediaSaveStatuses,
                    )
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    /** One-shot: the screen scrolls to it, then calls [onScrollTargetConsumed]. */
    val scrollTarget: StateFlow<ScrollTarget?> = scrollTargetFlow

    init {
        load()
        viewModelScope.launch { boardInfo.value = boardRepository.board(board) }
        viewModelScope.launch {
            topVisiblePostNo.filterNotNull().distinctUntilChanged().collectLatest { postNo ->
                delay(500)
                historyRepository.updateScrollPosition(board, threadNo, postNo)
            }
        }
        viewModelScope.launch {
            bottomVisiblePostNo.filterNotNull().distinctUntilChanged().collect(::raiseReadMark)
        }
    }

    private fun loadedPosts(): List<ThreadPost>? = (result.value as? DataResult.Success)?.value?.posts

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!forceRefresh) result.value = null
            val r = threadRepository.thread(board, threadNo, forceRefresh)
            if (r is DataResult.Success) {
                val newest = r.value.posts.maxOfOrNull { it.no } ?: 0L
                if (lastKnownPostNo != 0L && newest > lastKnownPostNo) {
                    val newOnes = r.value.posts.count { it.no > lastKnownPostNo }
                    newPostsAfter.value = lastKnownPostNo to newOnes
                    poller.resetBackoff()
                }
                lastKnownPostNo = newest
                recordHistory(r.value)
                // Viewing the thread clears its bookmark's unread badge (no-op if not bookmarked).
                if (newest > 0) {
                    bookmarkRepository.markSeen(board, threadNo, newest, r.value.posts.size - 1)
                }
                resolveScrollTarget(r.value)
            }
            if (r is DataResult.Failure && r.error == NetworkError.NotFound) {
                archivedNotice.value = true
                poller.stop()
                if (result.value is DataResult.Success) return@launch // keep showing content
            }
            result.value = r
        }
    }

    /**
     * Restore priority: explicit target (quote/history tap) > media last viewed in the
     * full-screen viewer > reading position saved on scroll. Re-runs on each successful
     * load and on return to the screen, so a viewer session lands on its last item.
     */
    private suspend fun resolveScrollTarget(details: ThreadDetails) {
        val mediaTarget = mediaSessionStore.consumeLastViewed(board, threadNo)
        if (restoredScroll && mediaTarget == null) return
        val target = when {
            !restoredScroll && initialPostNo != null -> initialPostNo
            mediaTarget != null -> mediaTarget
            !restoredScroll -> historyRepository.lastScrollPosition(board, threadNo)
            else -> null
        }
        restoredScroll = true
        if (target != null && details.posts.any { it.no == target }) {
            scrollTargetFlow.value = ScrollTarget(target, animate = false)
        }
    }

    fun onScrollTargetConsumed() {
        scrollTargetFlow.value = null
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

    /** Poll only while the thread is on screen (D17); the screen drives visibility. */
    fun onScreenVisibilityChanged(visible: Boolean) {
        if (visible) {
            poller.start(viewModelScope)
            // Returning from the media viewer: pick up its last-viewed item.
            (result.value as? DataResult.Success)?.let {
                viewModelScope.launch { resolveScrollTarget(it.value) }
            }
        } else {
            poller.stop()
        }
    }

    fun onToggleAutoRefresh() = viewModelScope.launch {
        val effective = autoRefreshUserOverride.value
            ?: settingsRepository.settings.first().autoRefreshEnabled
        autoRefreshUserOverride.value = !effective
        poller.resetBackoff()
    }

    fun onToggleBookmark() = viewModelScope.launch {
        val posts = loadedPosts() ?: return@launch
        if (bookmarked.first()) {
            bookmarkRepository.remove(board, threadNo)
        } else {
            val op = posts.firstOrNull() ?: return@launch
            bookmarkRepository.add(
                Bookmark(
                    board = board,
                    threadNo = threadNo,
                    subject = op.subject,
                    opExcerpt = op.body.plainText.take(200),
                    thumbnailUrl = op.media?.thumbnailUrl,
                    replyCount = posts.size - 1,
                    imageCount = posts.count { it.media != null },
                    bookmarkedAt = System.currentTimeMillis(),
                    lastCheckedAt = System.currentTimeMillis(),
                    lastSeenPostNo = posts.maxOfOrNull { it.no },
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
        val details = (result.value as? DataResult.Success)?.value ?: return
        val links = details.backlinks[postNo].orEmpty()
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
        val matches = searchMatches(loadedPosts() ?: return, searchQuery.value)
        if (matches.isEmpty()) return
        val next = (searchIndex.value + delta).mod(matches.size)
        searchIndex.value = next
        scrollTargetFlow.value = ScrollTarget(matches[next], animate = true)
    }

    /** External-link tap: trusted domains skip the dialog (D26). */
    fun onExternalLink(url: String): Boolean {
        val settings = settingsState.value
        val domain = Urls.domainOf(url)
        return if (!settings.confirmBeforeOpeningLinks || (domain != null && domain in settings.trustedDomains)) {
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

    /** The screen reports the visible index range; the VM owns what it means. */
    fun onVisiblePostsChanged(firstIndex: Int, lastIndex: Int?) {
        val posts = loadedPosts() ?: return
        // Top-of-screen post: the reading position restored when the thread is reopened.
        posts.getOrNull(firstIndex)?.let { topVisiblePostNo.value = it.no }
        // Bottom-of-screen post: the true "read up to" mark behind the bookmarks unread count.
        if (lastIndex != null) posts.getOrNull(lastIndex)?.let { bottomVisiblePostNo.value = it.no }
    }

    /**
     * Raises the "read up to" high-water mark and live-updates the bookmark's unread pill,
     * so counts are correct the moment the user returns to the bookmarks tab.
     */
    private suspend fun raiseReadMark(postNo: Long) {
        // Scrolling back up must not inflate the count — the mark only ever rises.
        val current = historyRepository.readUpTo(board, threadNo)
        if (current != null && postNo < current) return
        historyRepository.updateReadUpTo(board, threadNo, postNo)
        val remaining = loadedPosts()?.count { it.no > postNo } ?: return
        bookmarkRepository.updateUnread(board, threadNo, remaining)
    }

    fun onDismissNewPostsDivider() { newPostsAfter.value = null }

    private companion object {
        fun searchMatches(posts: List<ThreadPost>, query: String?): List<Long> =
            if (query.isNullOrBlank()) emptyList()
            else posts.filter {
                it.body.plainText.contains(query, true) || it.subject?.contains(query, true) == true
            }.map { it.no }
    }
}

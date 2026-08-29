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
import dev.stan.yotsuba.domain.model.PostGraph
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.ClaimedPostRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import dev.stan.yotsuba.feature.media.MediaSessionStore
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.update
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
    private val mediaVault: MediaVaultRepository,
    private val downloadQueue: MediaDownloadQueue,
    private val claimedPosts: ClaimedPostRepository,
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
    private val settingsState = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    private val boardInfo = MutableStateFlow<Board?>(null)
    /** Exposed for tests: every change to it is one atomic emission. */
    val session = MutableStateFlow(Session())

    private val scrollTargetFlow = MutableStateFlow<ScrollTarget?>(null)
    private val mediaToOpenFlow = MutableStateFlow<Long?>(null)
    private val topVisiblePostNo = MutableStateFlow<Long?>(null)
    private val bottomVisiblePostNo = MutableStateFlow<Long?>(null)

    private var restoredScroll = false

    private val poller = ThreadPoller(
        // A closed or archived thread will never gain posts; polling it is pure waste.
        isEnabled = {
            val details = (result.value as? DataResult.Success)?.value
            !session.value.archived && details?.closed != true && details?.archived != true &&
                autoRefreshOn(session.value, settingsState.value)
        },
        poll = { load(forceRefresh = true, quiet = true) },
    )

    private val bookmarked = bookmarkRepository.isBookmarked(board, threadNo)

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

    private val claimed = claimedPosts.claimed(board, threadNo)

    /** Slow-changing companions of the thread, folded so the top-level combine stays typed. */
    private val meta = combine(boardInfo, bookmarked, mediaSaveStatuses, claimed, ::Meta)
    private data class Meta(
        val board: Board?,
        val bookmarked: Boolean,
        val saveStatuses: Map<String, MediaSaveStatus>,
        val claimed: Set<Long>,
    )

    val uiState: StateFlow<UiState<ThreadContent>> = combine(
        result, settingsRepository.settings, meta, session,
    ) { res, settings, (board, bookmarked, saveStatuses, claimed), session ->
        when (res) {
            null -> UiState.Loading
            is DataResult.Failure -> UiState.Error(res.error)
            is DataResult.Success -> {
                val details = res.value
                val matches = searchMatches(details.posts, session.searchQuery)
                val byNo = details.posts.associateBy { it.no }
                UiState.Success(
                    ThreadContent(
                        details = details,
                        board = board,
                        bookmarked = bookmarked,
                        revealAllSpoilers = settings.revealAllSpoilers,
                        postStates = postStates(details, session, saveStatuses),
                        rows = rows(details, session),
                        treeView = session.treeView,
                        autoRefreshEnabled = autoRefreshOn(session, settings),
                        archivedNotice = session.archived || details.archived,
                        refreshError = session.refreshError,
                        refreshing = session.refreshing,
                        searchQuery = session.searchQuery,
                        searchMatches = matches,
                        searchIndex = if (matches.isEmpty()) 0 else session.searchIndex.coerceIn(0, matches.size - 1),
                        previewStack = session.previewPostNos
                            .map { group -> group.mapNotNull { byNo[it] } }
                            .filter { it.isNotEmpty() },
                        pendingExternalUrl = session.pendingExternalUrl,
                        filterPosterId = session.filterPosterId,
                        galleryOpen = session.galleryOpen,
                        postSheet = session.postSheetFor?.let { byNo[it] },
                        mediaPosts = details.posts.filter { it.presentMedia != null },
                        quoteLabels = quoteLabels(details, claimed),
                        claimedPostNos = claimed,
                        repliesToMe = details.posts.count { p ->
                            p.no !in claimed && p.quotedPostNos.any { it in claimed }
                        },
                    )
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    /** One-shot: the screen scrolls to it, then calls [onScrollTargetConsumed]. */
    val scrollTarget: StateFlow<ScrollTarget?> = scrollTargetFlow

    /** One-shot: the post whose media the viewer should open; the screen calls [onMediaOpened]. */
    val mediaToOpen: StateFlow<Long?> = mediaToOpenFlow

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

    /** Highest post number on screen right now; 0 before the first load. */
    private val newestLoadedPostNo: Long get() = loadedPosts()?.maxOfOrNull { it.no } ?: 0L

    /** [quiet] refreshes without the spinner: auto-polls should not flicker the indicator. */
    fun load(forceRefresh: Boolean = false, quiet: Boolean = false) {
        viewModelScope.launch {
            if (!forceRefresh) result.value = null else if (!quiet) session.update { it.copy(refreshing = true) }
            val r = threadRepository.thread(board, threadNo, forceRefresh)
            session.update { it.copy(refreshing = false) }
            when (r) {
                is DataResult.Success -> {
                    session.update { it.copy(refreshError = null) }
                    onLoaded(r.value)
                    result.value = r
                }
                is DataResult.Failure -> {
                    if (r.error == NetworkError.NotFound) {
                        session.update { it.copy(archived = true) }
                        poller.stop()
                    }
                    if (result.value !is DataResult.Success) {
                        result.value = r // nothing to keep on screen
                    } else if (r.error != NetworkError.NotFound) {
                        session.update { it.copy(refreshError = r.error) } // keep content, report transiently
                    }
                }
            }
        }
    }

    private suspend fun onLoaded(details: ThreadDetails) {
        // Runs before [result] is replaced, so this is still the previous load's newest post.
        val previous = newestLoadedPostNo
        val newest = details.posts.maxOfOrNull { it.no } ?: 0L
        if (previous != 0L && newest > previous) {
            val newOnes = details.posts.count { it.no > previous }
            session.update { it.copy(newPostsAfter = previous to newOnes) }
            poller.resetBackoff()
        }
        recordHistory(details)
        resolveScrollTarget(details)
    }

    /** The screen showed the refresh error; drop it so it is not shown again. */
    fun onRefreshErrorShown() = session.update { it.copy(refreshError = null) }

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
                thumbnailUrl = op.presentMedia?.thumbnailUrl,
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

    fun onToggleAutoRefresh() {
        val effective = autoRefreshOn(session.value, settingsState.value)
        session.update { it.copy(autoRefreshOverride = !effective) }
        poller.resetBackoff()
    }

    @Suppress("DEPRECATION") // lastSeenPostNo has no default; readUpTo is the live mark
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
                    thumbnailUrl = op.presentMedia?.thumbnailUrl,
                    replyCount = posts.size - 1,
                    imageCount = posts.count { it.media != null },
                    bookmarkedAt = System.currentTimeMillis(),
                    lastCheckedAt = System.currentTimeMillis(),
                    lastSeenPostNo = null,
                    state = BookmarkState.ALIVE,
                    // Seed the read mark from history so the new bookmark does not start all-unread.
                    readUpTo = historyRepository.readUpTo(board, threadNo),
                )
            )
        }
    }

    fun onRevealSpoiler(postNo: Long, spoilerId: Int) =
        session.update { it.copy(revealedText = it.revealedText + (postNo to spoilerId)) }

    fun onRevealImageSpoiler(postNo: Long) =
        session.update { it.copy(revealedImages = it.revealedImages + postNo) }

    /** A spoilered thumbnail reveals on the first tap and opens on the next. */
    fun onThumbnailTap(post: ThreadPost) {
        val media = post.presentMedia ?: return
        val hidden = media.spoiler && !settingsState.value.revealAllSpoilers &&
            post.no !in session.value.revealedImages
        if (hidden) onRevealImageSpoiler(post.no) else mediaToOpenFlow.value = post.no
    }

    fun onMediaOpened() { mediaToOpenFlow.value = null }

    /** True when the screen should save the attachment: the hold-to-save setting gates it. */
    fun onThumbnailLongPress(post: ThreadPost): Boolean =
        settingsState.value.holdToSave && post.presentMedia != null

    fun onOpenPreview(postNo: Long) =
        session.update { it.copy(previewPostNos = it.previewPostNos + listOf(listOf(postNo))) }

    fun onOpenBacklinks(postNo: Long) {
        val details = (result.value as? DataResult.Success)?.value ?: return
        val links = details.backlinks[postNo].orEmpty()
        if (links.isNotEmpty()) session.update { it.copy(previewPostNos = it.previewPostNos + listOf(links)) }
    }

    fun onClosePreview() = session.update { it.copy(previewPostNos = it.previewPostNos.dropLast(1)) }

    /** "Mark as mine" / "Not mine": flips whether [postNo] reads as the user's own post. */
    fun onToggleClaimed(postNo: Long) = viewModelScope.launch {
        if (postNo in claimed.first()) claimedPosts.unclaim(board, threadNo, postNo)
        else claimedPosts.claim(board, threadNo, postNo)
    }

    /** Tap on a poster-ID pill: show only that ID; tapping the same ID again clears it. */
    fun onFilterPosterId(posterId: String?) =
        session.update { it.copy(filterPosterId = if (it.filterPosterId == posterId) null else posterId) }

    private var highlightJob: Job? = null

    /**
     * Scrolls to [postNo] and flashes it (quotelink tap, backlink tap, preview "Go to").
     * Closes any open previews first: the jump is what the user asked for. Unknown posts
     * (a cross-thread stray, a pruned post) are ignored.
     */
    fun onJumpToPost(postNo: Long) {
        if (loadedPosts()?.none { it.no == postNo } != false) return
        session.update { it.copy(previewPostNos = emptyList(), highlightedPostNo = postNo) }
        scrollTargetFlow.value = ScrollTarget(postNo, animate = true)
        highlightJob?.cancel()
        highlightJob = viewModelScope.launch {
            delay(HIGHLIGHT_MS)
            session.update { if (it.highlightedPostNo == postNo) it.copy(highlightedPostNo = null) else it }
        }
    }

    /** Query and index change together: a stale index must never meet a new query. */
    fun onSearchChange(query: String?) = session.update { it.copy(searchQuery = query, searchIndex = 0) }

    fun onSearchStep(delta: Int) {
        val matches = searchMatches(loadedPosts() ?: return, session.value.searchQuery)
        if (matches.isEmpty()) return
        val next = (session.value.searchIndex + delta).mod(matches.size)
        session.update { it.copy(searchIndex = next) }
        scrollTargetFlow.value = ScrollTarget(matches[next], animate = true)
    }

    /** Routes a tapped link: 4chan links stay in the app, others go through [onExternalLink]. */
    fun onLinkTap(url: String): LinkAction {
        val internal = Urls.parseInternal(url)
        return when {
            internal != null -> LinkAction.Internal(internal)
            onExternalLink(url) -> LinkAction.External(url)
            else -> LinkAction.Confirm
        }
    }

    /** External-link tap: trusted domains skip the dialog (D26). */
    fun onExternalLink(url: String): Boolean {
        val settings = settingsState.value
        val domain = Urls.domainOf(url)
        return if (!settings.confirmBeforeOpeningLinks || (domain != null && domain in settings.trustedDomains)) {
            true // open immediately
        } else {
            session.update { it.copy(pendingExternalUrl = url) }
            false
        }
    }

    fun onDismissLinkDialog() = session.update { it.copy(pendingExternalUrl = null) }

    fun hasStorageAccess(): Boolean = mediaVault.hasStorageAccess()

    /** Queues a vault save for [post]'s attachment, with the thread context the vault files by. */
    fun onSaveMedia(post: ThreadPost) {
        val item = post.presentMedia ?: return
        val loaded = (result.value as? DataResult.Success)?.value
        val op = loaded?.posts?.firstOrNull { it.isOp }
        downloadQueue.enqueue(
            item,
            VaultSaveContext(
                board = board,
                threadNo = threadNo,
                threadSubject = op?.subject,
                opExcerpt = op?.body?.plainText?.takeIf { it.isNotBlank() },
                post = post,
                conversation = if (loaded != null && settingsState.value.saveRepliesWithMedia) {
                    PostGraph.of(loaded).conversationAround(post.no)
                } else {
                    emptyList()
                },
            ),
        )
    }

    fun onOpenPostSheet(postNo: Long) = session.update { it.copy(postSheetFor = postNo) }
    fun onClosePostSheet() = session.update { it.copy(postSheetFor = null) }

    fun onToggleTreeView() = session.update { it.copy(treeView = !it.treeView) }

    /** Expands the replies folded under [parentNo]'s "N more" row. */
    fun onExpandTail(parentNo: Long) = session.update { it.copy(expandedTails = it.expandedTails + parentNo) }

    fun onOpenGallery() = session.update { it.copy(galleryOpen = true) }
    fun onCloseGallery() = session.update { it.copy(galleryOpen = false) }

    /** Gallery tap: close the sheet and hand the post to the viewer. */
    fun onOpenMediaFromGallery(post: ThreadPost) {
        onCloseGallery()
        if (post.presentMedia != null) mediaToOpenFlow.value = post.no
    }

    /** Queues every attachment in the thread; already saved or queued items are skipped by the queue. */
    fun onSaveAllMedia() {
        loadedPosts()?.filter { it.presentMedia != null }?.forEach(::onSaveMedia)
    }

    fun onTrustDomain(url: String) = viewModelScope.launch {
        val domain = Urls.domainOf(url) ?: return@launch
        settingsRepository.update { it.copy(trustedDomains = it.trustedDomains + domain) }
        session.update { it.copy(pendingExternalUrl = null) }
    }

    /** The screen reports the visible row range; the VM owns what it means. */
    fun onVisiblePostsChanged(firstIndex: Int, lastIndex: Int?) {
        val details = (result.value as? DataResult.Success)?.value ?: return
        val rows = rows(details, session.value)
        // Top-of-screen post: the reading position restored when the thread is reopened.
        rows.postAt(firstIndex)?.let { topVisiblePostNo.value = it.no }
        // Bottom-of-screen post: the true "read up to" mark behind the bookmarks unread count.
        if (lastIndex != null) rows.postAt(lastIndex)?.let { bottomVisiblePostNo.value = it.no }
    }

    /**
     * Raises the "read up to" high-water mark in history and on the bookmark (a no-op when
     * the thread is not bookmarked), so unread counts are right the moment the user returns
     * to the bookmarks tab.
     */
    private suspend fun raiseReadMark(postNo: Long) {
        // Scrolling back up must not inflate the count — the mark only ever rises.
        val current = historyRepository.readUpTo(board, threadNo)
        if (current != null && postNo < current) return
        historyRepository.updateReadUpTo(board, threadNo, postNo)
        val replyCount = (loadedPosts()?.size ?: 1) - 1
        bookmarkRepository.markSeen(board, threadNo, postNo, replyCount)
    }

    fun onDismissNewPostsDivider() = session.update { it.copy(newPostsAfter = null) }

    companion object {
        private const val HIGHLIGHT_MS = 1_500L

        /** The poster-ID filter applied; the OP always stays so the thread keeps its header. */
        private fun visiblePosts(posts: List<ThreadPost>, session: Session): List<ThreadPost> {
            val id = session.filterPosterId ?: return posts
            return posts.filter { it.isOp || it.posterId == id }
        }

        /** Tree view indents this deep; anything deeper collapses into a "N more" row. */
        const val MAX_TREE_DEPTH = 4

        /** Linear: thread order with the new-posts divider. Tree: nested, capped, filtered. */
        private fun rows(details: ThreadDetails, session: Session): List<ThreadRow> =
            if (session.treeView) treeRows(details, session)
            else linearRows(visiblePosts(details.posts, session), session.newPostsAfter)

        /** Posts in thread order, with the new-posts divider just after [newPostsAfter]'s post. */
        private fun linearRows(posts: List<ThreadPost>, newPostsAfter: Pair<Long, Int>?): List<ThreadRow> =
            buildList {
                posts.forEach { post ->
                    add(ThreadRow.Post(post))
                    if (newPostsAfter != null && post.no == newPostsAfter.first) {
                        add(ThreadRow.NewPostsDivider(newPostsAfter.second))
                    }
                }
            }

        /**
         * Depth-first tree, indent capped at [MAX_TREE_DEPTH]. A capped post's deeper replies
         * follow it directly in the walk, so they fold into one "N more" row until expanded,
         * then show flattened at the cap. The ID filter applies as in the linear view; the
         * new-posts divider does not exist here since the order is no longer chronological.
         */
        private fun treeRows(details: ThreadDetails, session: Session): List<ThreadRow> {
            val visible = visiblePosts(details.posts, session).mapTo(HashSet()) { it.no }
            val nodes = PostGraph.of(details).tree().filter { it.post.no in visible }
            val out = mutableListOf<ThreadRow>()
            var i = 0
            while (i < nodes.size) {
                val node = nodes[i]
                out += ThreadRow.Post(node.post, node.depth.coerceAtMost(MAX_TREE_DEPTH))
                i++
                if (node.depth != MAX_TREE_DEPTH) continue
                val tailStart = i
                while (i < nodes.size && nodes[i].depth > MAX_TREE_DEPTH) i++
                val tail = nodes.subList(tailStart, i)
                if (tail.isEmpty()) continue
                if (node.post.no in session.expandedTails) {
                    tail.forEach { out += ThreadRow.Post(it.post, MAX_TREE_DEPTH) }
                } else {
                    out += ThreadRow.MoreReplies(node.post.no, tail.size)
                }
            }
            return out
        }

        /** The OP is labelled first; a claimed OP still reads as yours. */
        private fun quoteLabels(details: ThreadDetails, claimed: Set<Long>): Map<Long, QuoteLabel> = buildMap {
            details.posts.firstOrNull { it.isOp }?.let { put(it.no, QuoteLabel.OP) }
            claimed.forEach { put(it, QuoteLabel.YOU) }
        }

        private fun postStates(
            details: ThreadDetails,
            session: Session,
            saveStatuses: Map<String, MediaSaveStatus>,
        ): Map<Long, PostUiState> {
            val revealedText = session.revealedText.groupBy({ it.first }, { it.second })
            val idCounts = details.posts.mapNotNull { it.posterId }.groupingBy { it }.eachCount()
            return details.posts.associate { post ->
                post.no to PostUiState(
                    posterIdCount = post.posterId?.let { idCounts[it] } ?: 0,
                    closed = post.isOp && details.closed,
                    sticky = post.isOp && details.sticky,
                    revealedSpoilerIds = revealedText[post.no]?.toSet().orEmpty(),
                    imageSpoilerRevealed = post.no in session.revealedImages,
                    backlinks = details.backlinks[post.no].orEmpty(),
                    saveStatus = post.presentMedia?.fullUrl?.let { saveStatuses[it] },
                    highlighted = post.no == session.highlightedPostNo,
                )
            }
        }

        /** The post at a row index, or the nearest post above a divider. */
        private fun List<ThreadRow>.postAt(index: Int): ThreadPost? =
            (0..index).reversed().firstNotNullOfOrNull { (getOrNull(it) as? ThreadRow.Post)?.post }

        /** The user's in-thread toggle wins over the setting. */
        private fun autoRefreshOn(session: Session, settings: Settings): Boolean =
            session.autoRefreshOverride ?: settings.autoRefreshEnabled

        private fun searchMatches(posts: List<ThreadPost>, query: String?): List<Long> =
            if (query.isNullOrBlank()) emptyList()
            else posts.filter {
                it.body.plainText.contains(query, true) || it.subject?.contains(query, true) == true
            }.map { it.no }
    }
}

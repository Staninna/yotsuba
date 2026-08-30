package dev.stan.yotsuba.feature.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.di.ComputeDispatcher
import dev.stan.yotsuba.core.network.ArchiveHosts
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.FilterMatcher
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.QuoteTapAction
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.ClaimedPostRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.MediaSaveQueue
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import dev.stan.yotsuba.feature.media.MediaSessionStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val downloadQueue: MediaSaveQueue,
    private val claimedPosts: ClaimedPostRepository,
    /** Where the row pipeline runs; tests pass their scheduler's dispatcher. */
    @ComputeDispatcher private val compute: CoroutineDispatcher = Dispatchers.Default,
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
    /** Compiled once per change to the filter list, never per post. */
    private val matcher: StateFlow<FilterMatcher> = settingsRepository.settings
        .map { it.filters }
        .distinctUntilChanged()
        .map { FilterMatcher(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FilterMatcher.Empty)
    private val _session = MutableStateFlow(Session())
    /** Read by tests: every change to it is one atomic emission. */
    val session: StateFlow<Session> = _session.asStateFlow()

    private val scrollTargetFlow = MutableStateFlow<ScrollTarget?>(null)
    private val mediaToOpenFlow = MutableStateFlow<Long?>(null)
    private val topVisiblePostNo = MutableStateFlow<Long?>(null)
    private val bottomVisiblePostNo = MutableStateFlow<Long?>(null)

    private var restoredScroll = false

    /** The rows the latest emission put on screen: what the visible-index reports index into. */
    @Volatile private var lastRows: List<ThreadRow>? = null

    private val poller = ThreadPoller(
        // A closed or archived thread will never gain posts; polling it is pure waste.
        isEnabled = {
            val details = (result.value as? DataResult.Success)?.value
            !_session.value.archived && details?.closed != true && details?.archived != true &&
                details?.offlineCopy != true &&
                autoRefreshOn(_session.value, settingsState.value)
        },
        poll = { load(forceRefresh = true, quiet = true) },
    )

    private val bookmarked = bookmarkRepository.isBookmarked(board, threadNo)

    private val own = ThreadKey(board, threadNo)
    private val ghosts = GhostResolver(mediaVault, threadRepository)

    private val claimed = claimedPosts.claimed(board, threadNo)

    /** Slow-changing companions of the thread, folded so the top-level combine stays typed. */
    private val meta = combine(boardInfo, bookmarked, downloadQueue.statuses, claimed, mediaVault.saved(), ::Meta)
    private data class Meta(
        val board: Board?,
        val bookmarked: Boolean,
        val saveStatuses: Map<String, MediaSaveStatus>,
        val claimed: Set<Long>,
        /** URL -> vault file, for an expanded image to read from disk instead of the network. */
        val savedPaths: Map<String, String?>,
    )

    val uiState: StateFlow<UiState<ThreadContent>> = combine(
        result, settingsRepository.settings, meta, _session, matcher,
    ) { res, settings, (board, bookmarked, saveStatuses, claimed, savedPaths), session, matcher ->
        when (res) {
            null -> UiState.Loading
            is DataResult.Failure -> UiState.Error(res.error)
            is DataResult.Success -> {
                val details = res.value
                val matches = searchMatches(details.posts, session.searchQuery)
                val byNo = details.posts.associateBy { it.no }
                val verdicts = filterVerdicts(details.posts, matcher)
                val repliesToMe = details.posts.filter { p ->
                    p.no !in claimed && p.quotedPostNos.any { it in claimed }
                }
                val rows = threadRows(details, session, verdicts).also { lastRows = it }
                UiState.Success(
                    ThreadContent(
                        details = details,
                        board = board,
                        bookmarked = bookmarked,
                        revealAllSpoilers = settings.revealAllSpoilers,
                        postStates = postStates(details, session, saveStatuses, savedPaths, settings.dataSaver),
                        rows = rows,
                        filteredCount = verdicts.size,
                        treeView = session.treeView,
                        autoRefreshEnabled = autoRefreshOn(session, settings),
                        archivedNotice = session.archived || details.archived,
                        archiveUrl = details.archive?.let { ArchiveHosts.threadUrl(it, this@ThreadViewModel.board, threadNo) },
                        offlineCopyAt = session.offlineCopyAt.takeIf { details.offlineCopy },
                        refreshError = session.refreshError,
                        refreshing = session.refreshing,
                        searchQuery = session.searchQuery,
                        searchMatches = matches,
                        searchIndex = if (matches.isEmpty()) 0 else session.searchIndex.coerceIn(0, matches.size - 1),
                        preview = previewSheet(own, details, session),
                        pendingExternalUrl = session.pendingExternalUrl,
                        filterPosterId = session.filterPosterId,
                        galleryOpen = session.galleryOpen,
                        postSheet = session.postSheetFor?.let { byNo[it] },
                        mediaPosts = details.posts.filter { it.presentMedia != null },
                        quoteLabels = quoteLabels(details, claimed),
                        claimedPostNos = claimed,
                        repliesToMe = repliesToMe.size,
                        latestReplyToMe = repliesToMe.lastOrNull()?.no,
                    )
                )
            }
        }
    }
        // Deriving rows, verdicts and search matches over every post is real work; keep it
        // off the main thread (the block is pure, so only where it runs changes).
        .flowOn(compute)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    /** One-shot: the screen scrolls to it, then calls [onScrollTargetConsumed]. */
    val scrollTarget: StateFlow<ScrollTarget?> = scrollTargetFlow

    /** One-shot: the post whose media the viewer should open; the screen calls [onMediaOpened]. */
    val mediaToOpen: StateFlow<Long?> = mediaToOpenFlow

    init {
        load()
        viewModelScope.launch { boardInfo.value = boardRepository.board(board) }
        // Both positions settle before they are written: a fling reports every row it passes,
        // and each write is a database round trip. collectLatest restarts the wait on every
        // new row and runs the body for the last one once the list stops, so nothing is lost.
        viewModelScope.launch {
            topVisiblePostNo.filterNotNull().distinctUntilChanged().collectLatest { postNo ->
                delay(SCROLL_SETTLE_MS)
                historyRepository.updateScrollPosition(board, threadNo, postNo)
            }
        }
        viewModelScope.launch {
            bottomVisiblePostNo.filterNotNull().distinctUntilChanged().collectLatest { postNo ->
                delay(SCROLL_SETTLE_MS)
                // The two writes go together; a row arriving mid-way must not split them.
                withContext(NonCancellable) { raiseReadMark(postNo) }
            }
        }
    }

    private fun loadedPosts(): List<ThreadPost>? = (result.value as? DataResult.Success)?.value?.posts

    /** Highest post number on screen right now; 0 before the first load. */
    private val newestLoadedPostNo: Long get() = loadedPosts()?.maxOfOrNull { it.no } ?: 0L

    /** [quiet] refreshes without the spinner: auto-polls should not flicker the indicator. */
    fun load(forceRefresh: Boolean = false, quiet: Boolean = false) {
        viewModelScope.launch {
            if (!forceRefresh) result.value = null else if (!quiet) _session.update { it.copy(refreshing = true) }
            var r = threadRepository.thread(board, threadNo, forceRefresh)
            if (r is DataResult.Failure && result.value !is DataResult.Success) r = fallback(r)
            _session.update { it.copy(refreshing = false) }
            when (r) {
                is DataResult.Success -> {
                    _session.update { it.copy(refreshError = null) }
                    onLoaded(r.value)
                    result.value = r
                }
                is DataResult.Failure -> {
                    if (r.error == NetworkError.NotFound) {
                        _session.update { it.copy(archived = true) }
                        poller.stop()
                    }
                    if (result.value !is DataResult.Success) {
                        result.value = r // nothing to keep on screen
                    } else if (r.error != NetworkError.NotFound) {
                        _session.update { it.copy(refreshError = r.error) } // keep content, report transiently
                    }
                }
            }
        }
    }

    /** Error-state retry: bypass the cache like pull-to-refresh, but show the loading shell. */
    fun retry() {
        result.value = null
        load(forceRefresh = true, quiet = true)
    }

    /**
     * What to show when 4chan did not answer: the vault's own copy first (it works offline
     * and is what the user chose to keep), then, once 4chan says the thread is gone, an
     * archive's copy. Otherwise the failure itself. Neither copy ever polls.
     */
    private suspend fun fallback(failure: DataResult.Failure): DataResult<ThreadDetails> {
        mediaVault.savedThread(board, threadNo)?.let { saved ->
            _session.update { it.copy(offlineCopyAt = savedAt(saved)) }
            return DataResult.Success(saved.copy(offlineCopy = true))
        }
        if (failure.error != NetworkError.NotFound) return failure
        val archived = threadRepository.archivedThread(board, threadNo)
        return if (archived is DataResult.Success) archived else failure
    }

    /** When the offline copy was taken: the newest save in the thread, else its newest post. */
    private suspend fun savedAt(saved: ThreadDetails): Long? =
        mediaVault.entries().first()
            .filter { it.location.board == board && it.location.threadNo == threadNo }
            .maxOfOrNull { it.savedAt }
            ?: saved.posts.maxOfOrNull { it.timeSeconds * 1000 }

    private suspend fun onLoaded(details: ThreadDetails) {
        // Runs before [result] is replaced, so this is still the previous load's newest post.
        val previous = newestLoadedPostNo
        val newest = details.posts.maxOfOrNull { it.no } ?: 0L
        if (previous != 0L && newest > previous) {
            val newOnes = details.posts.count { it.no > previous }
            _session.update { it.copy(newPostsAfter = previous to newOnes) }
            poller.resetBackoff()
        }
        if (previous == 0L) collapseReadPosts(details)
        recordHistory(details)
        resolveScrollTarget(details)
    }

    /**
     * First load of a watched thread: fold what was read last time so it opens at the new
     * replies. Only when there are some; a fully read thread was opened to be re-read.
     */
    private suspend fun collapseReadPosts(details: ThreadDetails) {
        if (!settingsRepository.settings.first().collapseReadPosts) return
        if (!bookmarked.first()) return
        val mark = historyRepository.readUpTo(board, threadNo) ?: return
        val body = details.posts.drop(1)
        if (body.none { it.no <= mark } || body.none { it.no > mark }) return
        _session.update { it.copy(collapsedUpTo = mark) }
    }

    /** A jump or search hit inside the folded run unfolds it; the target has to be on screen. */
    private fun unfoldFor(postNo: Long) {
        val mark = _session.value.collapsedUpTo ?: return
        if (postNo <= mark) _session.update { it.copy(collapsedUpTo = null) }
    }

    fun onExpandEarlier() = _session.update { it.copy(collapsedUpTo = null) }

    /** The screen showed the refresh error; drop it so it is not shown again. */
    fun onRefreshErrorShown() = _session.update { it.copy(refreshError = null) }

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
            unfoldFor(target)
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
        val effective = autoRefreshOn(_session.value, settingsState.value)
        _session.update { it.copy(autoRefreshOverride = !effective) }
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
                    thumbnailUrl = op.presentMedia?.thumbnailUrl,
                    replyCount = posts.size - 1,
                    imageCount = posts.count { it.media != null },
                    bookmarkedAt = System.currentTimeMillis(),
                    lastCheckedAt = System.currentTimeMillis(),
                    state = BookmarkState.ALIVE,
                    // Seed the read mark from history so the new bookmark does not start all-unread.
                    readUpTo = historyRepository.readUpTo(board, threadNo),
                )
            )
        }
    }

    fun onRevealSpoiler(postNo: Long, spoilerId: Int) =
        _session.update { it.copy(revealedText = it.revealedText + (postNo to spoilerId)) }

    fun onRevealImageSpoiler(postNo: Long) =
        _session.update { it.copy(revealedImages = it.revealedImages + postNo) }

    /** See [thumbnailTap] for what a tap does; a spoiler reveals first, then it expands or opens. */
    fun onThumbnailTap(post: ThreadPost) {
        val media = post.presentMedia ?: return
        when (thumbnailTap(media, post.no, _session.value, settingsState.value)) {
            ThumbnailTap.REVEAL_SPOILER -> onRevealImageSpoiler(post.no)
            ThumbnailTap.COLLAPSE -> _session.update { it.copy(expandedImages = it.expandedImages - post.no) }
            ThumbnailTap.EXPAND -> _session.update { it.copy(expandedImages = it.expandedImages + post.no) }
            ThumbnailTap.OPEN_VIEWER -> mediaToOpenFlow.value = post.no
        }
    }

    /** The full image shown in the card was tapped: the viewer opens on it. */
    fun onExpandedImageTap(post: ThreadPost) {
        if (post.presentMedia != null) mediaToOpenFlow.value = post.no
    }

    fun onMediaOpened() { mediaToOpenFlow.value = null }

    /** True when the screen should save the attachment: the hold-to-save setting gates it. */
    fun onThumbnailLongPress(post: ThreadPost): Boolean =
        settingsState.value.holdToSave && post.presentMedia != null

    /**
     * Focuses [postNo] in the preview sheet, on top of whatever it showed before, so the
     * sheet's back arrow returns there. The post is read in the thread of the post on top
     * of the sheet (a quote inside a ghost post stays in the ghost's thread), or in this
     * thread when the sheet is closed. Refocusing the post already on top is a no-op, and a
     * post of this thread that it does not have (a pruned or cross-thread stray) opens nothing.
     */
    fun onOpenPreview(postNo: Long) {
        val context = _session.value.previewPath.lastOrNull()?.key ?: own
        if (context != own) return onOpenGhost(context.board, context.threadNo, postNo)
        if (loadedPosts()?.none { it.no == postNo } != false) return
        push(PreviewRef(board, threadNo, postNo))
    }

    /**
     * Focuses a post of another thread in the sheet, fetching that thread if this screen
     * has not seen it yet. A quote to a whole thread previews its OP.
     */
    fun onOpenGhost(board: String, threadNo: Long, postNo: Long?) {
        push(PreviewRef(board, threadNo, postNo ?: threadNo))
        resolveGhost(board, threadNo, postNo ?: threadNo, skipLive = false)
    }

    /**
     * Tap on a cross-thread quotelink in the list. True when the sheet took it; false means
     * the setting asks for a jump, and the only jump to another thread is to open it.
     */
    fun onCrossThreadQuoteTap(board: String, threadNo: Long, postNo: Long?): Boolean {
        if (settingsState.value.quoteTap == QuoteTapAction.JUMP) return false
        onOpenGhost(board, threadNo, postNo)
        return true
    }

    /**
     * Tap on a `>>123` deadlink: 4chan pruned the post, so the sheet looks for it in the
     * vault's and the archive's copy of the thread it was quoted in, never live.
     */
    fun onDeadlinkTap(postNo: Long) {
        val context = _session.value.previewPath.lastOrNull()?.key ?: own
        push(PreviewRef(context.board, context.threadNo, postNo))
        resolveGhost(context.board, context.threadNo, postNo, skipLive = true)
    }

    private fun push(ref: PreviewRef) = _session.update {
        if (it.previewPath.lastOrNull() == ref) it else it.copy(previewPath = it.previewPath + ref)
    }

    /**
     * Fills the session's ghost cache for [postNo]'s thread, unless a copy holding the post
     * is already there. A copy without it (a partial snapshot) is looked up again, since a
     * fuller source may have it; what the screen already holds is always tried first.
     */
    private fun resolveGhost(board: String, threadNo: Long, postNo: Long, skipLive: Boolean) {
        val key = ThreadKey(board, threadNo)
        val held = when {
            key == own -> (result.value as? DataResult.Success)?.value
            else -> (_session.value.ghosts[key] as? GhostState.Loaded)?.details
        }
        if (held?.posts?.any { it.no == postNo } == true) {
            if (key != own) _session.update { it.copy(ghosts = it.ghosts + (key to GhostState.Loaded(held))) }
            return
        }
        if (_session.value.ghosts[key] == GhostState.Loading) return
        _session.update { it.copy(ghosts = it.ghosts + (key to GhostState.Loading)) }
        viewModelScope.launch {
            val state = when (val r = ghosts.resolve(board, threadNo, postNo, held, skipLive)) {
                is DataResult.Success -> GhostState.Loaded(r.value)
                // A copy already on hand stays useful for the posts it does have.
                is DataResult.Failure -> if (held != null) GhostState.Loaded(held) else GhostState.Failed(r.error)
            }
            _session.update { it.copy(ghosts = it.ghosts + (key to state)) }
        }
    }

    /** Tap on a same-thread quotelink: whatever [Settings.quoteTap] says. */
    fun onQuoteTap(postNo: Long) = quoteAction(settingsState.value.quoteTap, postNo)

    /** Long-press on a same-thread quotelink: the action the tap does not do. */
    fun onQuoteLongPress(postNo: Long) = quoteAction(settingsState.value.quoteTap.other(), postNo)

    private fun quoteAction(action: QuoteTapAction, postNo: Long) = when (action) {
        QuoteTapAction.POPOVER -> onOpenPreview(postNo)
        QuoteTapAction.JUMP -> onJumpToPost(postNo)
    }

    /** The sheet's back arrow and system back: return to the previously focused post. */
    fun onClosePreview() = _session.update { it.copy(previewPath = it.previewPath.dropLast(1)) }

    /** The sheet was swiped away or its scrim tapped: the whole path goes. */
    fun onDismissPreview() = _session.update { it.copy(previewPath = emptyList()) }

    /** "Mark as mine" / "Not mine": flips whether [postNo] reads as the user's own post. */
    fun onToggleClaimed(postNo: Long) = viewModelScope.launch {
        if (postNo in claimed.first()) claimedPosts.unclaim(board, threadNo, postNo)
        else claimedPosts.claim(board, threadNo, postNo)
    }

    /** Tap on a poster-ID pill: show only that ID; tapping the same ID again clears it. */
    fun onFilterPosterId(posterId: String?) =
        _session.update { it.copy(filterPosterId = if (it.filterPosterId == posterId) null else posterId) }

    private var highlightJob: Job? = null

    /**
     * Scrolls to [postNo] and flashes it (quotelink tap, backlink tap, preview "Go to").
     * Closes any open previews first: the jump is what the user asked for. Unknown posts
     * (a cross-thread stray, a pruned post) are ignored.
     */
    fun onJumpToPost(postNo: Long) {
        if (loadedPosts()?.none { it.no == postNo } != false) return
        _session.update { it.copy(previewPath = emptyList(), highlightedPostNo = postNo) }
        unfoldFor(postNo)
        scrollTargetFlow.value = ScrollTarget(postNo, animate = true)
        highlightJob?.cancel()
        highlightJob = viewModelScope.launch {
            delay(HIGHLIGHT_MS)
            _session.update { if (it.highlightedPostNo == postNo) it.copy(highlightedPostNo = null) else it }
        }
    }

    /** Query and index change together: a stale index must never meet a new query. */
    fun onSearchChange(query: String?) = _session.update { it.copy(searchQuery = query, searchIndex = 0) }

    fun onSearchStep(delta: Int) {
        val matches = searchMatches(loadedPosts() ?: return, _session.value.searchQuery)
        if (matches.isEmpty()) return
        val next = (_session.value.searchIndex + delta).mod(matches.size)
        _session.update { it.copy(searchIndex = next) }
        unfoldFor(matches[next])
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
            _session.update { it.copy(pendingExternalUrl = url) }
            false
        }
    }

    fun onDismissLinkDialog() = _session.update { it.copy(pendingExternalUrl = null) }

    fun hasStorageAccess(): Boolean = mediaVault.hasStorageAccess()

    /** Queues a vault save for [post]'s attachment, with the thread context the vault files by. */
    fun onSaveMedia(post: ThreadPost) {
        val item = post.presentMedia ?: return
        val loaded = (result.value as? DataResult.Success)?.value
        downloadQueue.enqueue(
            item,
            VaultSaveContext.of(board, threadNo, loaded, post, settingsState.value.saveRepliesWithMedia),
        )
    }

    fun onOpenPostSheet(postNo: Long) = _session.update { it.copy(postSheetFor = postNo) }
    fun onClosePostSheet() = _session.update { it.copy(postSheetFor = null) }

    fun onToggleTreeView() = _session.update { it.copy(treeView = !it.treeView) }

    /** Expands the replies folded under [parentNo]'s "N more" row. */
    fun onExpandTail(parentNo: Long) = _session.update { it.copy(expandedTails = it.expandedTails + parentNo) }

    /** Opens a stubbed post in place; it stays open for the rest of the session. */
    fun onExpandFiltered(postNo: Long) =
        _session.update { it.copy(expandedFiltered = it.expandedFiltered + postNo) }

    fun onOpenGallery() = _session.update { it.copy(galleryOpen = true) }
    fun onCloseGallery() = _session.update { it.copy(galleryOpen = false) }

    /** Gallery tap: close the sheet and hand the post to the viewer. */
    fun onOpenMediaFromGallery(post: ThreadPost) {
        onCloseGallery()
        if (post.presentMedia != null) mediaToOpenFlow.value = post.no
    }

    /**
     * Queues the attachments of [posts] (by default every one in the thread); already saved
     * or queued items are skipped by the queue.
     */
    fun onSaveAllMedia(posts: List<ThreadPost>? = loadedPosts()) {
        posts?.filter { it.presentMedia != null }?.forEach(::onSaveMedia)
    }

    fun onTrustDomain(url: String) = viewModelScope.launch {
        val domain = Urls.domainOf(url) ?: return@launch
        settingsRepository.update { it.copy(trustedDomains = it.trustedDomains + domain) }
        _session.update { it.copy(pendingExternalUrl = null) }
    }

    /** The screen reports the visible row range; the VM owns what it means. */
    fun onVisiblePostsChanged(firstIndex: Int, lastIndex: Int?) {
        // Before the first emission there is nothing on screen to report; the next range
        // report after it lands (every scroll produces one) is what counts.
        val rows = lastRows ?: return
        // Top-of-screen post: the reading position restored when the thread is reopened.
        rows.postAt(firstIndex)?.let { topVisiblePostNo.value = it.no }
        // Bottom-of-screen post: the true "read up to" mark behind the bookmarks unread count.
        // An ID-filtered or tree-ordered list is not the thread in reading order, so its
        // bottom row says nothing about how far the user has read: the mark stays put.
        val session = _session.value
        if (lastIndex == null || session.filterPosterId != null || session.treeView) return
        rows.postAt(lastIndex)?.let { bottomVisiblePostNo.value = it.no }
    }

    /**
     * Raises the "read up to" high-water mark in history and on the bookmark (a no-op when
     * the thread is not bookmarked), so unread counts are right the moment the user returns
     * to the bookmarks tab.
     */
    private suspend fun raiseReadMark(postNo: Long) {
        // Scrolling back up must not inflate the count. The mark only ever rises.
        val current = historyRepository.readUpTo(board, threadNo)
        if (current != null && postNo < current) return
        historyRepository.updateReadUpTo(board, threadNo, postNo)
        bookmarkRepository.markSeen(board, threadNo, postNo)
    }

    fun onDismissNewPostsDivider() = _session.update { it.copy(newPostsAfter = null) }

    private companion object {
        const val HIGHLIGHT_MS = 1_500L
        /** How long the list has to sit still before its visible range is written down. */
        const val SCROLL_SETTLE_MS = 500L

        /** The user's in-thread toggle wins over the setting. */
        private fun autoRefreshOn(session: Session, settings: Settings): Boolean =
            session.autoRefreshOverride ?: settings.autoRefreshEnabled
    }
}

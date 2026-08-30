package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.core.text.PostSegment
import dev.stan.yotsuba.core.text.PostText
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.data.repository.MediaDownloadQueue
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRefreshSummary
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.ClaimedPostRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.fake.FakeMediaVault
import dev.stan.yotsuba.domain.repository.ThreadRepository
import dev.stan.yotsuba.fake.FakeSettings
import dev.stan.yotsuba.fake.NoDedup
import dev.stan.yotsuba.feature.media.MediaSessionStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class FakeThreadRepository(details: ThreadDetails) : ThreadRepository {
    var result: DataResult<ThreadDetails> = DataResult.Success(details)
    var archived: DataResult<ThreadDetails> = DataResult.Failure(NetworkError.NotFound)
    /** Every source asked, in order, so a test can assert the fallback order. */
    val asked = mutableListOf<String>()
    override suspend fun thread(board: String, no: Long, forceRefresh: Boolean): DataResult<ThreadDetails> {
        asked += "live"
        return result
    }
    override suspend fun archivedThread(board: String, no: Long): DataResult<ThreadDetails> {
        asked += "archive"
        return archived
    }
}

object FakeBoardRepository : BoardRepository {
    override suspend fun boards(forceRefresh: Boolean) = DataResult.Success(emptyList<Board>())
    override suspend fun board(code: String): Board? = null
}

class FakeBookmarkRepository : BookmarkRepository {
    val bookmarkedFlow = MutableStateFlow(false)
    var added: Bookmark? = null
    var removedCount = 0
    override val bookmarks: Flow<List<Bookmark>> = flowOf(emptyList())
    override suspend fun add(bookmark: Bookmark) {
        added = bookmark
        bookmarkedFlow.value = true
    }
    override suspend fun remove(board: String, threadNo: Long) {
        removedCount++
        bookmarkedFlow.value = false
    }
    override fun isBookmarked(board: String, threadNo: Long): Flow<Boolean> = bookmarkedFlow
    override suspend fun refreshAll(onProgress: (Int, Int) -> Unit) = BookmarkRefreshSummary()
    /** Every markSeen call, in order; the repository itself never lowers the mark. */
    val seen = mutableListOf<Long>()
    override suspend fun markSeen(board: String, threadNo: Long, lastSeenPostNo: Long) {
        seen += lastSeenPostNo
    }
    override suspend fun setPinned(board: String, threadNo: Long, pinned: Boolean) {}
    override suspend fun removeDead() {}
    override suspend fun clearAll() {}
}

class FakeHistoryRepository(var savedScrollPostNo: Long? = null) : HistoryRepository {
    var readMark: Long? = null
    override val history: Flow<List<HistoryEntry>> = flowOf(emptyList())
    override suspend fun record(entry: HistoryEntry) {}
    override suspend fun updateScrollPosition(board: String, threadNo: Long, postNo: Long) {
        savedScrollPostNo = postNo
    }
    override suspend fun lastScrollPosition(board: String, threadNo: Long) = savedScrollPostNo
    override suspend fun updateReadUpTo(board: String, threadNo: Long, postNo: Long) { readMark = postNo }
    override suspend fun readUpTo(board: String, threadNo: Long) = readMark
    override suspend fun remove(board: String, threadNo: Long) {}
    override suspend fun restore(entry: HistoryEntry) = record(entry)
    override suspend fun clearAll() {}
    override suspend fun trim(retainAfterMs: Long) {}
}

/** Records every save in order; `statuses` is too transient to assert on. */
class FakeVault : FakeMediaVault() {
    val saves = mutableListOf<Pair<MediaItem, VaultSaveContext>>()
    override fun hasStorageAccess() = false
    override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? {
        saves += item to context
        return null
    }
    var snapshot: ThreadDetails? = null
    override suspend fun savedThread(board: String, threadNo: Long): ThreadDetails? = snapshot
}

class FakeClaimedPosts : ClaimedPostRepository {
    val state = MutableStateFlow<Set<Long>>(emptySet())
    override fun claimed(board: String, threadNo: Long): Flow<Set<Long>> = state
    override suspend fun claim(board: String, threadNo: Long, postNo: Long) { state.value += postNo }
    override suspend fun unclaim(board: String, threadNo: Long, postNo: Long) { state.value -= postNo }
}

/** Everything a [ThreadViewModel] needs, faked; every thread test builds its ViewModel here. */
class ThreadEnv(
    posts: List<ThreadPost> = (100L..104L).map { post(it) },
    backlinks: Map<Long, List<Long>> = emptyMap(),
    val history: FakeHistoryRepository = FakeHistoryRepository(),
    val sessionStore: MediaSessionStore = MediaSessionStore(),
    val bookmarks: FakeBookmarkRepository = FakeBookmarkRepository(),
    val settings: FakeSettings = FakeSettings(),
    val claimed: FakeClaimedPosts = FakeClaimedPosts(),
    /** Unconfined keeps the row pipeline on the test scheduler, so emissions stay deterministic. */
    val compute: CoroutineDispatcher = Dispatchers.Unconfined,
    /** Where the save queue's worker runs; a save test hands it `backgroundScope` and the test dispatcher, then `runCurrent`s. */
    queueScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    io: CoroutineDispatcher = Dispatchers.Unconfined,
) {
    val threads = FakeThreadRepository(
        ThreadDetails("g", 100, posts, archived = false, closed = false, backlinks = backlinks)
    )

    fun details(posts: List<ThreadPost>) =
        ThreadDetails("g", 100, posts, archived = false, closed = false, backlinks = emptyMap())

    val vault = FakeVault()
    val queue = MediaDownloadQueue(vault, NoDedup, queueScope, io)

    fun vm(initialPostNo: Long? = null) = ThreadViewModel(
        board = "g", threadNo = 100, initialPostNo = initialPostNo,
        threadRepository = threads,
        boardRepository = FakeBoardRepository,
        bookmarkRepository = bookmarks,
        historyRepository = history,
        settingsRepository = settings,
        mediaSessionStore = sessionStore,
        mediaVault = vault,
        downloadQueue = queue,
        claimedPosts = claimed,
        compute = compute,
    )

    /** A ViewModel whose `uiState` is kept collected on [scope], so its `stateIn` stays live. */
    fun collectedVm(scope: CoroutineScope, initialPostNo: Long? = null): ThreadViewModel =
        vm(initialPostNo).also { vm -> scope.launch { vm.uiState.collect {} } }

    companion object {
        fun post(no: Long) = ThreadPost(
            board = "g", no = no, isOp = false, name = "Anonymous", tripcode = null,
            capcode = null, posterId = null, countryCode = null, countryName = null,
            timeSeconds = 0, subject = null,
            body = PostText(listOf(PostSegment(if (no % 2 == 0L) "match $no" else "other $no"))),
            media = null, quotedPostNos = emptyList(),
        )

        fun postWithMedia(no: Long) = post(no).copy(
            media = PostMedia.Present(
                MediaItem(
                    postNo = no, filename = "pic", ext = ".jpg", sizeBytes = 10,
                    width = 100, height = 100,
                    thumbnailUrl = "https://i.4cdn.org/g/${no}s.jpg",
                    fullUrl = "https://i.4cdn.org/g/$no.jpg",
                    spoiler = false,
                ),
            ),
        )
    }
}

/** The loaded thread content; fails loudly when the state is not [UiState.Success]. */
fun content(vm: ThreadViewModel): ThreadContent = (vm.uiState.value as UiState.Success<ThreadContent>).data

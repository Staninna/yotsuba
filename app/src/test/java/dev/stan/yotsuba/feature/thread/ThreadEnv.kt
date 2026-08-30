package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.data.repository.MediaDownloadQueue
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.PostGraph
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.PostSegment
import dev.stan.yotsuba.domain.model.PostText
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRefreshSummary
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.ClaimedPostRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import dev.stan.yotsuba.fake.FakeBoardRepository
import dev.stan.yotsuba.fake.FakeBookmarkRepository
import dev.stan.yotsuba.fake.FakeHistoryRepository
import dev.stan.yotsuba.fake.FakeMediaVault
import dev.stan.yotsuba.fake.FakeSettings
import dev.stan.yotsuba.fake.FakeThreadRepository
import dev.stan.yotsuba.fake.FakeVault
import dev.stan.yotsuba.fake.NoDedup
import dev.stan.yotsuba.feature.media.MediaSessionStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher

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
    /**
     * Eager like Unconfined, so row emissions stay deterministic, but its delays are virtual.
     * `stateIn(WhileSubscribed)` inherits this context from `flowOn(compute)`; on the real
     * Unconfined its five-second stop timer ran on wall-clock time and fired after
     * `resetMain`, crashing whichever test was running then.
     */
    val compute: CoroutineDispatcher = UnconfinedTestDispatcher(),
    /** Where the save queue's worker runs; a save test hands it `backgroundScope` and the test dispatcher, then `runCurrent`s. */
    queueScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    io: CoroutineDispatcher = Dispatchers.Unconfined,
) {
    val threads = FakeThreadRepository(
        ThreadDetails("g", 100, posts, archived = false, closed = false, backlinks = backlinks)
    )

    fun details(posts: List<ThreadPost>, board: String = "g", threadNo: Long = 100) =
        ThreadDetails(board, threadNo, posts, archived = false, closed = false, backlinks = PostGraph.backlinksOf(posts))

    val vault = FakeVault(access = false)
    val queue = MediaDownloadQueue(vault, NoDedup, queueScope, io)

    fun vm(initialPostNo: Long? = null) = ThreadViewModel(
        board = "g", threadNo = 100, initialPostNo = initialPostNo,
        threadRepository = threads,
        boardRepository = FakeBoardRepository(),
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

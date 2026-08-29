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
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import dev.stan.yotsuba.feature.media.MediaSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A refresh that fails must not take the loaded thread off screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadRefreshFailureTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val threads = object : ThreadRepository {
        var result: DataResult<ThreadDetails> = DataResult.Success(
            ThreadDetails("g", 100, (100L..104L).map(::post), archived = false, closed = false, backlinks = emptyMap()),
        )
        override suspend fun thread(board: String, no: Long, forceRefresh: Boolean) = result
    }

    private val vault = object : MediaVaultRepository {
        override fun hasStorageAccess() = false
        override fun entries(): Flow<List<VaultEntry>> = flowOf(emptyList())
        override fun savedUrls(): Flow<Set<String>> = flowOf(emptySet())
        override fun savedPaths(): Flow<Map<String, String>> = flowOf(emptyMap())
        override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? = null
        override suspend fun delete(url: String): VaultError? = null
        override suspend fun syncSavedThreads(onProgress: (Int, Int) -> Unit) = VaultSyncSummary()
        override suspend fun importLocalThread(name: String, sources: List<ImportSource>): VaultError? = null
        override suspend fun savedThread(board: String, threadNo: Long): ThreadDetails? = null
        override suspend fun rescan() {}
        override suspend fun migrateLegacyIfNeeded() {}
    }

    private fun vm() = ThreadViewModel(
        board = "g", threadNo = 100, initialPostNo = null,
        threadRepository = threads,
        boardRepository = object : BoardRepository {
            override suspend fun boards(forceRefresh: Boolean) = DataResult.Success(emptyList<Board>())
            override suspend fun board(code: String): Board? = null
        },
        bookmarkRepository = object : BookmarkRepository {
            override val bookmarks: Flow<List<Bookmark>> = flowOf(emptyList())
            override suspend fun add(bookmark: Bookmark) {}
            override suspend fun remove(board: String, threadNo: Long) {}
            override fun isBookmarked(board: String, threadNo: Long): Flow<Boolean> = flowOf(false)
            override suspend fun refreshOne(bookmark: Bookmark) = bookmark
            override suspend fun markSeen(board: String, threadNo: Long, lastSeenPostNo: Long, replyCount: Int) {}
            @Deprecated("Unread is derived from readUpTo; use markSeen")
            override suspend fun updateUnread(board: String, threadNo: Long, unread: Int) = Unit
            override suspend fun setPinned(board: String, threadNo: Long, pinned: Boolean) {}
            override suspend fun removeDead() {}
            override suspend fun clearAll() {}
        },
        historyRepository = object : HistoryRepository {
            override val history: Flow<List<HistoryEntry>> = flowOf(emptyList())
            override suspend fun record(entry: HistoryEntry) {}
            override suspend fun updateScrollPosition(board: String, threadNo: Long, postNo: Long) {}
            override suspend fun lastScrollPosition(board: String, threadNo: Long): Long? = null
            override suspend fun updateReadUpTo(board: String, threadNo: Long, postNo: Long) {}
            override suspend fun readUpTo(board: String, threadNo: Long): Long? = null
            override suspend fun remove(board: String, threadNo: Long) {}
            override suspend fun clearAll() {}
            override suspend fun trim(retainAfterMs: Long) {}
        },
        settingsRepository = object : SettingsRepository {
            val state = MutableStateFlow(Settings())
            override val settings: Flow<Settings> = state
            override suspend fun update(transform: (Settings) -> Settings) { state.value = transform(state.value) }
        },
        mediaSessionStore = MediaSessionStore(),
        mediaVault = vault,
        downloadQueue = MediaDownloadQueue(vault),
    )

    private fun content(vm: ThreadViewModel) = (vm.uiState.value as UiState.Success<ThreadContent>).data

    @Test fun `failed poll after a successful load keeps the posts and reports the error`() =
        runTest(dispatcher.scheduler) {
            val vm = vm()
            backgroundScope.launch { vm.uiState.collect {} }
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(5, content(vm).details.posts.size)

            threads.result = DataResult.Failure(NetworkError.Offline)
            vm.load(forceRefresh = true)
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(vm.uiState.value is UiState.Success)
            assertEquals(5, content(vm).details.posts.size)
            assertEquals(NetworkError.Offline, content(vm).refreshError)
            assertFalse(content(vm).archivedNotice)

            vm.onRefreshErrorShown()
            dispatcher.scheduler.advanceUntilIdle()
            assertNull(content(vm).refreshError)
        }

    @Test fun `failed first load still shows the error screen`() = runTest(dispatcher.scheduler) {
        threads.result = DataResult.Failure(NetworkError.Timeout)
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(UiState.Error(NetworkError.Timeout), vm.uiState.value)
    }

    private companion object {
        fun post(no: Long) = ThreadPost(
            board = "g", no = no, isOp = no == 100L, name = "Anonymous", tripcode = null,
            capcode = null, posterId = null, countryCode = null, countryName = null,
            timeSeconds = 0, subject = null, body = PostText(listOf(PostSegment("post $no"))),
            media = null, quotedPostNos = emptyList(),
        )
    }
}

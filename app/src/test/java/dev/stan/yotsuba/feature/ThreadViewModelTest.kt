package dev.stan.yotsuba.feature

import dev.stan.yotsuba.core.text.PostSegment
import dev.stan.yotsuba.core.text.PostText
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.data.repository.MediaDownloadQueue
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import dev.stan.yotsuba.feature.media.MediaSessionStore
import dev.stan.yotsuba.feature.thread.ThreadViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeThreadRepository(var details: ThreadDetails) : ThreadRepository {
        override suspend fun thread(board: String, no: Long, forceRefresh: Boolean) =
            DataResult.Success(details)
    }

    private object FakeBoardRepository : BoardRepository {
        override suspend fun boards(forceRefresh: Boolean) = DataResult.Success(emptyList<Board>())
        override suspend fun board(code: String): Board? = null
    }

    private class FakeBookmarkRepository : BookmarkRepository {
        override val bookmarks: Flow<List<Bookmark>> = flowOf(emptyList())
        override suspend fun add(bookmark: Bookmark) {}
        override suspend fun remove(board: String, threadNo: Long) {}
        override fun isBookmarked(board: String, threadNo: Long) = flowOf(false)
        override suspend fun refreshOne(bookmark: Bookmark) = bookmark
        override suspend fun markSeen(board: String, threadNo: Long, lastSeenPostNo: Long, replyCount: Int) {}
        var unread: Int? = null
        override suspend fun updateUnread(board: String, threadNo: Long, unread: Int) { this.unread = unread }
        override suspend fun clearAll() {}
    }

    private class FakeHistoryRepository(var savedScrollPostNo: Long? = null) : HistoryRepository {
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
        override suspend fun clearAll() {}
        override suspend fun trim(retainAfterMs: Long) {}
    }

    private class FakeSettingsRepository : SettingsRepository {
        val state = MutableStateFlow(Settings())
        override val settings: Flow<Settings> = state
        override suspend fun update(transform: (Settings) -> Settings) {
            state.value = transform(state.value)
        }
    }

    private object FakeVault : MediaVaultRepository {
        override fun hasStorageAccess() = false
        override fun entries(): Flow<List<VaultEntry>> = flowOf(emptyList())
        override fun savedUrls(): Flow<Set<String>> = flowOf(emptySet())
        override fun savedPaths(): Flow<Map<String, String>> = flowOf(emptyMap())
        override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? = null
        override suspend fun delete(url: String): VaultError? = null
        override suspend fun rescan() {}
        override suspend fun migrateLegacyIfNeeded() {}
    }

    private class Env(
        posts: List<ThreadPost> = (100L..104L).map { post(it) },
        val history: FakeHistoryRepository = FakeHistoryRepository(),
        val sessionStore: MediaSessionStore = MediaSessionStore(),
        val bookmarks: FakeBookmarkRepository = FakeBookmarkRepository(),
    ) {
        val threads = FakeThreadRepository(
            ThreadDetails("g", 100, posts, archived = false, closed = false, backlinks = emptyMap())
        )

        fun vm(initialPostNo: Long? = null) = ThreadViewModel(
            board = "g", threadNo = 100, initialPostNo = initialPostNo,
            threadRepository = threads,
            boardRepository = FakeBoardRepository,
            bookmarkRepository = bookmarks,
            historyRepository = history,
            settingsRepository = FakeSettingsRepository(),
            mediaSessionStore = sessionStore,
            mediaVault = FakeVault,
            downloadQueue = MediaDownloadQueue(FakeVault),
        )

        companion object {
            private fun post(no: Long) = ThreadPost(
                board = "g", no = no, isOp = false, name = "Anonymous", tripcode = null,
                capcode = null, posterId = null, countryCode = null, countryName = null,
                timeSeconds = 0, subject = null,
                body = PostText(listOf(PostSegment(if (no % 2 == 0L) "match $no" else "other $no"))),
                media = null, quotedPostNos = emptyList(),
            )
        }
    }

    @Test fun `search step wraps around the matches and emits an animated scroll target`() =
        runTest(dispatcher.scheduler) {
            val env = Env()
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()
            vm.onSearchChange("match") // posts 100, 102, 104
            vm.onSearchStep(1)
            assertEquals(102L, vm.scrollTarget.value?.postNo)
            assertEquals(true, vm.scrollTarget.value?.animate)
            vm.onSearchStep(1)
            assertEquals(104L, vm.scrollTarget.value?.postNo)
            vm.onSearchStep(1) // wraps forward
            assertEquals(100L, vm.scrollTarget.value?.postNo)
            vm.onSearchStep(-1) // wraps backward
            assertEquals(104L, vm.scrollTarget.value?.postNo)
        }

    @Test fun `search step is a no-op without matches`() = runTest(dispatcher.scheduler) {
        val vm = Env().vm()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onSearchChange("nothing-here")
        vm.onSearchStep(1)
        assertNull(vm.scrollTarget.value)
    }

    @Test fun `explicit navigation target wins over the saved reading position`() =
        runTest(dispatcher.scheduler) {
            val env = Env(history = FakeHistoryRepository(savedScrollPostNo = 103))
            val vm = env.vm(initialPostNo = 102)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(102L, vm.scrollTarget.value?.postNo)
            assertEquals(false, vm.scrollTarget.value?.animate)
        }

    @Test fun `saved reading position restores when there is no explicit target`() =
        runTest(dispatcher.scheduler) {
            val env = Env(history = FakeHistoryRepository(savedScrollPostNo = 103))
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(103L, vm.scrollTarget.value?.postNo)
        }

    @Test fun `last viewed media wins on return to the screen`() = runTest(dispatcher.scheduler) {
        val env = Env(history = FakeHistoryRepository(savedScrollPostNo = 101))
        val vm = env.vm()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(101L, vm.scrollTarget.value?.postNo)
        vm.onScrollTargetConsumed()
        env.sessionStore.setLastViewed("g", 100, 104)
        vm.onScreenVisibilityChanged(true)
        dispatcher.scheduler.runCurrent()
        assertEquals(104L, vm.scrollTarget.value?.postNo)
        vm.onScreenVisibilityChanged(false)
    }

    @Test fun `scroll target is skipped when the post is not in the thread`() =
        runTest(dispatcher.scheduler) {
            val env = Env(history = FakeHistoryRepository(savedScrollPostNo = 999))
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()
            assertNull(vm.scrollTarget.value)
        }

    @Test fun `read mark only rises and updates the bookmark unread count`() =
        runTest(dispatcher.scheduler) {
            val env = Env()
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()
            vm.onVisiblePostsChanged(0, 3) // bottom post 103 -> 1 unread below
            dispatcher.scheduler.runCurrent()
            assertEquals(103L, env.history.readMark)
            assertEquals(1, env.bookmarks.unread)
            vm.onVisiblePostsChanged(0, 1) // scrolling back up must not lower the mark
            dispatcher.scheduler.runCurrent()
            assertEquals(103L, env.history.readMark)
            assertEquals(1, env.bookmarks.unread)
        }
}

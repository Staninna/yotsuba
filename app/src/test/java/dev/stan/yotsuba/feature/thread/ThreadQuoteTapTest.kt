package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.core.text.PostSegment
import dev.stan.yotsuba.core.text.PostText
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.data.repository.MediaDownloadQueue
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.QuoteTapAction
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.ClaimedPostRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A quotelink tap follows [Settings.quoteTap]; a long-press does the other thing. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadQuoteTapTest {

    private val settingsState = MutableStateFlow(Settings())


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
        override fun saved(): Flow<Map<String, String?>> = flowOf(emptyMap())
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
            override val settings: Flow<Settings> = settingsState
            override suspend fun update(transform: (Settings) -> Settings) { settingsState.value = transform(settingsState.value) }
        },
        mediaSessionStore = MediaSessionStore(),
        mediaVault = vault,
        downloadQueue = MediaDownloadQueue(vault),
        claimedPosts = object : ClaimedPostRepository {
            override fun claimed(board: String, threadNo: Long): Flow<Set<Long>> = flowOf(emptySet())
            override suspend fun claim(board: String, threadNo: Long, postNo: Long) {}
            override suspend fun unclaim(board: String, threadNo: Long, postNo: Long) {}
        },
    )

    private fun content(vm: ThreadViewModel) = (vm.uiState.value as UiState.Success<ThreadContent>).data

    private fun previewNos(vm: ThreadViewModel) = content(vm).preview?.path.orEmpty()

    @Test fun `popover setting opens the preview on tap and jumps on long-press`() = runTest(dispatcher.scheduler) {
        settingsState.value = Settings(quoteTap = QuoteTapAction.POPOVER)
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        vm.onQuoteTap(102)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(102L), previewNos(vm))

        vm.onQuoteLongPress(103)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(content(vm).preview)
        assertEquals(103L, vm.scrollTarget.value?.postNo)
    }

    @Test fun `jump setting jumps on tap and opens the preview on long-press`() = runTest(dispatcher.scheduler) {
        settingsState.value = Settings(quoteTap = QuoteTapAction.JUMP)
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        vm.onQuoteTap(102)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(content(vm).preview)
        assertEquals(102L, vm.scrollTarget.value?.postNo)

        vm.onQuoteLongPress(103)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(103L), previewNos(vm))
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

package dev.stan.yotsuba.feature

import dev.stan.yotsuba.core.text.PostSegment
import dev.stan.yotsuba.core.text.PostText
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.data.repository.MediaDownloadQueue
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRefreshSummary
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import dev.stan.yotsuba.feature.media.MediaSessionStore
import dev.stan.yotsuba.feature.thread.LinkAction
import dev.stan.yotsuba.feature.thread.Session
import dev.stan.yotsuba.feature.thread.ThreadRow
import dev.stan.yotsuba.feature.thread.ThreadContent
import dev.stan.yotsuba.feature.thread.ThreadViewModel
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

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeThreadRepository(details: ThreadDetails) : ThreadRepository {
        var result: DataResult<ThreadDetails> = DataResult.Success(details)
        override suspend fun thread(board: String, no: Long, forceRefresh: Boolean) = result
    }

    private object FakeBoardRepository : BoardRepository {
        override suspend fun boards(forceRefresh: Boolean) = DataResult.Success(emptyList<Board>())
        override suspend fun board(code: String): Board? = null
    }

    private class FakeBookmarkRepository : BookmarkRepository {
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
        override suspend fun refreshOne(bookmark: Bookmark) = bookmark
        override suspend fun refreshAll(onProgress: (Int, Int) -> Unit) = BookmarkRefreshSummary()
        /** Every markSeen call, in order; the repository itself never lowers the mark. */
        val seen = mutableListOf<Long>()
        override suspend fun markSeen(board: String, threadNo: Long, lastSeenPostNo: Long, replyCount: Int) {
            seen += lastSeenPostNo
        }
        @Deprecated("Unread is derived from readUpTo; use markSeen")
        override suspend fun updateUnread(board: String, threadNo: Long, unread: Int) = Unit
        override suspend fun setPinned(board: String, threadNo: Long, pinned: Boolean) {}
        override suspend fun removeDead() {}
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

    /** Records the first save so a test can await it; `statuses` is too transient to assert on. */
    private class FakeVault : MediaVaultRepository {
        val firstSave = kotlinx.coroutines.CompletableDeferred<Pair<MediaItem, VaultSaveContext>>()
        override fun hasStorageAccess() = false
        override fun entries(): Flow<List<VaultEntry>> = flowOf(emptyList())
        override fun savedUrls(): Flow<Set<String>> = flowOf(emptySet())
        override fun savedPaths(): Flow<Map<String, String>> = flowOf(emptyMap())
        override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? {
            firstSave.complete(item to context)
            return null
        }
        override suspend fun delete(url: String): VaultError? = null
        override suspend fun syncSavedThreads(onProgress: (Int, Int) -> Unit) = VaultSyncSummary()
        override suspend fun importLocalThread(name: String, sources: List<ImportSource>): VaultError? = null
        override suspend fun savedThread(board: String, threadNo: Long): ThreadDetails? = null
        override suspend fun rescan() {}
        override suspend fun migrateLegacyIfNeeded() {}
    }

    private class Env(
        posts: List<ThreadPost> = (100L..104L).map { post(it) },
        backlinks: Map<Long, List<Long>> = emptyMap(),
        val history: FakeHistoryRepository = FakeHistoryRepository(),
        val sessionStore: MediaSessionStore = MediaSessionStore(),
        val bookmarks: FakeBookmarkRepository = FakeBookmarkRepository(),
        val settings: FakeSettingsRepository = FakeSettingsRepository(),
    ) {
        val threads = FakeThreadRepository(
            ThreadDetails("g", 100, posts, archived = false, closed = false, backlinks = backlinks)
        )

        fun details(posts: List<ThreadPost>) =
            ThreadDetails("g", 100, posts, archived = false, closed = false, backlinks = emptyMap())

        val vault = FakeVault()
        val queue = MediaDownloadQueue(vault)

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
        )

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
                        thumbnailUrl = "https://i.4cdn.org/g/${'$'}{no}s.jpg",
                        fullUrl = "https://i.4cdn.org/g/$no.jpg",
                        spoiler = false,
                    ),
                ),
            )
        }
    }

    @Test fun `saving queues the attachment, and a post without media is a no-op`() =
        runTest(dispatcher.scheduler) {
            val env = Env()
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()

            vm.onSaveMedia(Env.post(101))
            assertEquals(false, env.vault.firstSave.isCompleted)

            vm.onSaveMedia(Env.postWithMedia(100))
            val (item, context) = env.vault.firstSave.await()
            assertEquals("https://i.4cdn.org/g/100.jpg", item.fullUrl)
            assertEquals(100L, context.post?.no)
            assertEquals("g", context.board)
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

    @Test fun `a new query and its reset index arrive in one session emission`() =
        runTest(dispatcher.scheduler) {
            val vm = Env().vm()
            dispatcher.scheduler.advanceUntilIdle()
            vm.onSearchChange("match")
            vm.onSearchStep(1)
            vm.onSearchStep(1) // index 2
            val seen = mutableListOf<Session>()
            val job = launch(Dispatchers.Unconfined) { vm.session.collect { seen += it } }
            vm.onSearchChange("other")
            job.cancel()
            // Never (query = "other", index = 2): the two fields changed together.
            assertEquals(listOf(2, 0), seen.map { it.searchIndex })
            assertEquals(listOf("match", "other"), seen.map { it.searchQuery })
        }

    @Test fun `a spoilered thumbnail reveals on the first tap and opens on the second`() =
        runTest(dispatcher.scheduler) {
            val env = Env()
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()
            val spoilered = Env.postWithMedia(100).let {
                it.copy(media = PostMedia.Present((it.media as PostMedia.Present).item.copy(spoiler = true)))
            }
            vm.onThumbnailTap(spoilered)
            assertNull(vm.mediaToOpen.value)
            assertTrue(100L in vm.session.value.revealedImages)
            vm.onThumbnailTap(spoilered)
            assertEquals(100L, vm.mediaToOpen.value)
            vm.onMediaOpened()
            assertNull(vm.mediaToOpen.value)
        }

    @Test fun `hold to save is gated by the setting and by having media`() =
        runTest(dispatcher.scheduler) {
            val env = Env()
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(vm.onThumbnailLongPress(Env.postWithMedia(100)))
            assertFalse(vm.onThumbnailLongPress(Env.post(101)))
            env.settings.state.value = Settings(holdToSave = false)
            dispatcher.scheduler.advanceUntilIdle()
            assertFalse(vm.onThumbnailLongPress(Env.postWithMedia(100)))
        }

    @Test fun `links route to the app, straight out, or to the confirmation dialog`() =
        runTest(dispatcher.scheduler) {
            val env = Env()
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(vm.onLinkTap("https://boards.4chan.org/g/thread/100") is LinkAction.Internal)
            assertEquals(LinkAction.Confirm, vm.onLinkTap("https://example.com/page"))
            assertEquals("https://example.com/page", vm.session.value.pendingExternalUrl)
            vm.onTrustDomain("https://example.com/page")
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(LinkAction.External("https://example.com/other"), vm.onLinkTap("https://example.com/other"))
        }

    @Test fun `jumping to a post closes previews, scrolls, and highlights briefly`() =
        runTest(dispatcher.scheduler) {
            val env = Env()
            val vm = env.vm()
            backgroundScope.launch { vm.uiState.collect {} }
            dispatcher.scheduler.advanceUntilIdle()
            vm.onOpenPreview(101)
            vm.onJumpToPost(103)
            dispatcher.scheduler.runCurrent()
            assertEquals(0, content(vm).previewStack.size)
            assertEquals(103L, vm.scrollTarget.value?.postNo)
            assertTrue(content(vm).postStates.getValue(103L).highlighted)
            dispatcher.scheduler.advanceUntilIdle()
            assertFalse(content(vm).postStates.getValue(103L).highlighted)

            vm.onJumpToPost(999) // not in the thread: ignored
            assertEquals(103L, vm.scrollTarget.value?.postNo)
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

    @Test fun `read mark only rises and is forwarded to the bookmark`() =
        runTest(dispatcher.scheduler) {
            val env = Env()
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(emptyList<Long>(), env.bookmarks.seen) // opening marks nothing read
            vm.onVisiblePostsChanged(0, 3) // bottom post 103
            dispatcher.scheduler.runCurrent()
            assertEquals(103L, env.history.readMark)
            assertEquals(listOf(103L), env.bookmarks.seen)
            vm.onVisiblePostsChanged(0, 1) // scrolling back up must not lower the mark
            dispatcher.scheduler.runCurrent()
            assertEquals(103L, env.history.readMark)
            assertEquals(listOf(103L), env.bookmarks.seen)
        }

    private fun content(vm: ThreadViewModel): ThreadContent =
        (vm.uiState.value as UiState.Success).data

    @Test fun `bookmarking captures a snapshot of the loaded thread and toggles off again`() =
        runTest(dispatcher.scheduler) {
            val env = Env()
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()

            vm.onToggleBookmark()
            dispatcher.scheduler.advanceUntilIdle()
            val added = env.bookmarks.added!!
            assertEquals("g", added.board)
            assertEquals(100L, added.threadNo)
            assertEquals(4, added.replyCount) // 5 posts minus the OP
            assertEquals(0, added.imageCount)
            assertEquals("match 100", added.opExcerpt)

            vm.onToggleBookmark()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, env.bookmarks.removedCount)
        }

    @Test fun `refresh with new posts raises the divider at the old newest post`() =
        runTest(dispatcher.scheduler) {
            val env = Env()
            val vm = env.vm()
            backgroundScope.launch { vm.uiState.collect {} }
            dispatcher.scheduler.advanceUntilIdle()

            env.threads.result = DataResult.Success(env.details((100L..106L).map { Env.post(it) }))
            vm.load(forceRefresh = true)
            dispatcher.scheduler.advanceUntilIdle()

            val rows = content(vm).rows
            assertEquals(8, rows.size)
            assertEquals(ThreadRow.Post(Env.post(104)), rows[4])
            assertEquals(ThreadRow.NewPostsDivider(2), rows[5])
            assertEquals(ThreadRow.Post(Env.post(105)), rows[6])

            vm.onDismissNewPostsDivider()
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(content(vm).rows.none { it is ThreadRow.NewPostsDivider })
        }

    @Test fun `404 during refresh keeps the loaded posts and shows the archived notice`() =
        runTest(dispatcher.scheduler) {
            val env = Env()
            val vm = env.vm()
            backgroundScope.launch { vm.uiState.collect {} }
            dispatcher.scheduler.advanceUntilIdle()

            env.threads.result = DataResult.Failure(NetworkError.NotFound)
            vm.load(forceRefresh = true)
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(vm.uiState.value is UiState.Success)
            assertEquals(5, content(vm).details.posts.size)
            assertTrue(content(vm).archivedNotice)
        }

    @Test fun `untrusted links are intercepted until their domain is trusted`() =
        runTest(dispatcher.scheduler) {
            val env = Env()
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(vm.onExternalLink("https://example.com/page"))
            vm.onTrustDomain("https://example.com/page")
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue("example.com" in env.settings.state.value.trustedDomains)
            assertTrue(vm.onExternalLink("https://example.com/other"))
        }

    @Test fun `link confirmation off opens links immediately`() = runTest(dispatcher.scheduler) {
        val env = Env()
        env.settings.state.value = Settings(confirmBeforeOpeningLinks = false)
        val vm = env.vm()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.onExternalLink("https://example.com/page"))
    }

    @Test fun `backlink previews push and pop, and empty backlinks push nothing`() =
        runTest(dispatcher.scheduler) {
            val env = Env(backlinks = mapOf(101L to listOf(103L)))
            val vm = env.vm()
            backgroundScope.launch { vm.uiState.collect {} }
            dispatcher.scheduler.advanceUntilIdle()

            vm.onOpenBacklinks(100) // no backlinks recorded for the OP
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(0, content(vm).previewStack.size)

            vm.onOpenBacklinks(101)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf(103L), content(vm).previewStack.single().map { it.no })

            vm.onOpenPreview(102)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(2, content(vm).previewStack.size)

            vm.onClosePreview()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, content(vm).previewStack.size)
        }
}

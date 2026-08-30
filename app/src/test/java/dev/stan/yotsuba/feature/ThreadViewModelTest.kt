package dev.stan.yotsuba.feature

import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.domain.model.ArchiveSource
import dev.stan.yotsuba.domain.model.Filter
import dev.stan.yotsuba.domain.model.FilterAction
import dev.stan.yotsuba.domain.model.PostGraph
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.feature.thread.FakeHistoryRepository
import dev.stan.yotsuba.feature.thread.LinkAction
import dev.stan.yotsuba.feature.thread.QuoteLabel
import dev.stan.yotsuba.feature.thread.Session
import dev.stan.yotsuba.feature.thread.ThreadEnv
import dev.stan.yotsuba.feature.thread.ThreadRow
import dev.stan.yotsuba.feature.thread.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @Test fun `saving queues the attachment, and a post without media is a no-op`() =
        runTest(dispatcher.scheduler) {
            val env = ThreadEnv()
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()

            vm.onSaveMedia(ThreadEnv.post(101))
            assertEquals(false, env.vault.firstSave.isCompleted)

            vm.onSaveMedia(ThreadEnv.postWithMedia(100))
            val (item, context) = env.vault.firstSave.await()
            assertEquals("https://i.4cdn.org/g/100.jpg", item.fullUrl)
            assertEquals(100L, context.post?.no)
            assertEquals("g", context.board)
        }

    @Test fun `search step wraps around the matches and emits an animated scroll target`() =
        runTest(dispatcher.scheduler) {
            val env = ThreadEnv()
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
            val vm = ThreadEnv().vm()
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
            val env = ThreadEnv()
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()
            val spoilered = ThreadEnv.postWithMedia(100).let {
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
            val env = ThreadEnv()
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(vm.onThumbnailLongPress(ThreadEnv.postWithMedia(100)))
            assertFalse(vm.onThumbnailLongPress(ThreadEnv.post(101)))
            env.settings.state.value = Settings(holdToSave = false)
            dispatcher.scheduler.advanceUntilIdle()
            assertFalse(vm.onThumbnailLongPress(ThreadEnv.postWithMedia(100)))
        }

    @Test fun `links route to the app, straight out, or to the confirmation dialog`() =
        runTest(dispatcher.scheduler) {
            val env = ThreadEnv()
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
            val env = ThreadEnv()
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()
            vm.onOpenPreview(101)
            vm.onJumpToPost(103)
            dispatcher.scheduler.runCurrent()
            assertNull(content(vm).preview)
            assertEquals(103L, vm.scrollTarget.value?.postNo)
            assertTrue(content(vm).postStates.getValue(103L).highlighted)
            dispatcher.scheduler.advanceUntilIdle()
            assertFalse(content(vm).postStates.getValue(103L).highlighted)

            vm.onJumpToPost(999) // not in the thread: ignored
            assertEquals(103L, vm.scrollTarget.value?.postNo)
        }

    @Test fun `the OP gets an OP quote label`() = runTest(dispatcher.scheduler) {
        val env = ThreadEnv(posts = listOf(ThreadEnv.post(100).copy(isOp = true)) + (101L..103L).map { ThreadEnv.post(it) })
        val vm = env.collectedVm(backgroundScope)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(mapOf(100L to QuoteLabel.OP), content(vm).quoteLabels)
    }

    @Test fun `claimed posts read as You and their replies are counted`() = runTest(dispatcher.scheduler) {
        // 102 quotes 100 and 101; 103 quotes 102; 104 quotes nothing.
        val posts = listOf(
            ThreadEnv.post(100).copy(isOp = true), ThreadEnv.post(101),
            ThreadEnv.post(102).copy(quotedPostNos = listOf(100, 101)),
            ThreadEnv.post(103).copy(quotedPostNos = listOf(102)), ThreadEnv.post(104),
        )
        val env = ThreadEnv(posts = posts)
        val vm = env.collectedVm(backgroundScope)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, content(vm).repliesToMe)

        vm.onToggleClaimed(101)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(mapOf(100L to QuoteLabel.OP, 101L to QuoteLabel.YOU), content(vm).quoteLabels)
        assertEquals(setOf(101L), content(vm).claimedPostNos)
        assertEquals(1, content(vm).repliesToMe) // only 102
        assertEquals(102L, content(vm).latestReplyToMe)

        vm.onToggleClaimed(102) // a claimed reply to a claimed post is not a reply "to you"
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, content(vm).repliesToMe) // only 103 now
        assertEquals(103L, content(vm).latestReplyToMe)
        assertEquals(QuoteLabel.YOU, content(vm).quoteLabels[102L])

        vm.onToggleClaimed(101)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(content(vm).quoteLabels[101L])
        assertEquals(103L, content(vm).latestReplyToMe) // 102 is still claimed, and 103 quotes it
    }

    @Test fun `filtering by poster ID keeps the OP and that ID's posts, and counts the ID`() =
        runTest(dispatcher.scheduler) {
            val posts = listOf(
                ThreadEnv.post(100).copy(isOp = true, posterId = "AAAA"),
                ThreadEnv.post(101).copy(posterId = "BBBB"),
                ThreadEnv.post(102).copy(posterId = "AAAA"),
                ThreadEnv.post(103).copy(posterId = "BBBB"),
                ThreadEnv.post(104).copy(posterId = "BBBB"),
            )
            val env = ThreadEnv(posts = posts)
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(3, content(vm).postStates.getValue(101L).posterIdCount)

            vm.onFilterPosterId("BBBB")
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("BBBB", content(vm).filterPosterId)
            assertEquals(listOf(100L, 101L, 103L, 104L), content(vm).rows.map { (it as ThreadRow.Post).post.no })

            vm.onFilterPosterId("BBBB") // same ID again toggles the filter off
            dispatcher.scheduler.advanceUntilIdle()
            assertNull(content(vm).filterPosterId)
            assertEquals(5, content(vm).rows.size)
        }

    @Test fun `closed and sticky flags land on the OP card only`() = runTest(dispatcher.scheduler) {
        val env = ThreadEnv(posts = listOf(ThreadEnv.post(100).copy(isOp = true), ThreadEnv.post(101)))
        env.threads.result = DataResult.Success(
            env.details(listOf(ThreadEnv.post(100).copy(isOp = true), ThreadEnv.post(101))).copy(closed = true, sticky = true),
        )
        val vm = env.collectedVm(backgroundScope)
        dispatcher.scheduler.advanceUntilIdle()
        val op = content(vm).postStates.getValue(100L)
        assertTrue(op.closed && op.sticky)
        val reply = content(vm).postStates.getValue(101L)
        assertFalse(reply.closed || reply.sticky)
    }

    @Test fun `save all queues every attachment once`() = runTest(dispatcher.scheduler) {
        val env = ThreadEnv(posts = listOf(ThreadEnv.postWithMedia(100), ThreadEnv.post(101), ThreadEnv.postWithMedia(102)))
        val vm = env.collectedVm(backgroundScope)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(100L, 102L), content(vm).mediaPosts.map { it.no })
        env.vault.expectedSaves = 2
        vm.onSaveAllMedia()
        assertEquals(
            listOf("https://i.4cdn.org/g/100.jpg", "https://i.4cdn.org/g/102.jpg"),
            env.vault.allSaved.await(),
        )
    }

    @Test fun `tree view nests replies, caps the depth behind a more row, and expands it`() =
        runTest(dispatcher.scheduler) {
            // 100 <- 101 <- 102 <- 103 <- 104 <- 105 <- 106 (a chain), plus 107 replying to the OP.
            val chain = (101L..106L).map { ThreadEnv.post(it).copy(quotedPostNos = listOf(it - 1)) }
            val posts = listOf(ThreadEnv.post(100).copy(isOp = true)) + chain +
                ThreadEnv.post(107).copy(quotedPostNos = listOf(100))
            val env = ThreadEnv(posts = posts)
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()

            vm.onToggleTreeView()
            dispatcher.scheduler.advanceUntilIdle()
            val rows = content(vm).rows
            assertEquals(
                listOf(
                    ThreadRow.Post(posts[0], 0), ThreadRow.Post(posts[1], 1), ThreadRow.Post(posts[2], 2),
                    ThreadRow.Post(posts[3], 3), ThreadRow.Post(posts[4], 4),
                    ThreadRow.MoreReplies(parentNo = 104, count = 2),
                    ThreadRow.Post(posts[7], 1),
                ),
                rows,
            )

            vm.onExpandTail(104)
            dispatcher.scheduler.advanceUntilIdle()
            val expanded = content(vm).rows
            assertEquals(listOf(100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L), expanded.map { (it as ThreadRow.Post).post.no })
            assertEquals(listOf(0, 1, 2, 3, 4, 4, 4, 1), expanded.map { (it as ThreadRow.Post).depth })

            vm.onToggleTreeView() // back to linear: flat, chronological
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(List(8) { 0 }, content(vm).rows.map { (it as ThreadRow.Post).depth })
        }

    @Test fun `search step is a no-op without matches`() = runTest(dispatcher.scheduler) {
        val vm = ThreadEnv().vm()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onSearchChange("nothing-here")
        vm.onSearchStep(1)
        assertNull(vm.scrollTarget.value)
    }

    @Test fun `explicit navigation target wins over the saved reading position`() =
        runTest(dispatcher.scheduler) {
            val env = ThreadEnv(history = FakeHistoryRepository(savedScrollPostNo = 103))
            val vm = env.vm(initialPostNo = 102)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(102L, vm.scrollTarget.value?.postNo)
            assertEquals(false, vm.scrollTarget.value?.animate)
        }

    @Test fun `saved reading position restores when there is no explicit target`() =
        runTest(dispatcher.scheduler) {
            val env = ThreadEnv(history = FakeHistoryRepository(savedScrollPostNo = 103))
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(103L, vm.scrollTarget.value?.postNo)
        }

    @Test fun `last viewed media wins on return to the screen`() = runTest(dispatcher.scheduler) {
        val env = ThreadEnv(history = FakeHistoryRepository(savedScrollPostNo = 101))
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
            val env = ThreadEnv(history = FakeHistoryRepository(savedScrollPostNo = 999))
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()
            assertNull(vm.scrollTarget.value)
        }

    @Test fun `read mark only rises and is forwarded to the bookmark`() =
        runTest(dispatcher.scheduler) {
            val env = ThreadEnv()
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

    @Test fun `the read mark ignores the bottom of an ID-filtered or tree-ordered list`() =
        runTest(dispatcher.scheduler) {
            val posts = listOf(
                ThreadEnv.post(100).copy(isOp = true, posterId = "AAAA"),
                ThreadEnv.post(101).copy(posterId = "BBBB"),
                ThreadEnv.post(102).copy(posterId = "BBBB"),
                ThreadEnv.post(103).copy(posterId = "BBBB"),
                ThreadEnv.post(104).copy(posterId = "AAAA"),
            )
            val env = ThreadEnv(posts = posts)
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()

            vm.onFilterPosterId("AAAA") // rows: 100, 104
            dispatcher.scheduler.advanceUntilIdle()
            vm.onVisiblePostsChanged(0, 1)
            dispatcher.scheduler.runCurrent()
            assertNull(env.history.readMark)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(100L, env.history.savedScrollPostNo) // the reading position still tracks the top

            vm.onFilterPosterId(null)
            vm.onToggleTreeView()
            dispatcher.scheduler.advanceUntilIdle()
            vm.onVisiblePostsChanged(0, 4)
            dispatcher.scheduler.runCurrent()
            assertNull(env.history.readMark)

            vm.onToggleTreeView()
            dispatcher.scheduler.advanceUntilIdle()
            vm.onVisiblePostsChanged(0, 2)
            dispatcher.scheduler.runCurrent()
            assertEquals(102L, env.history.readMark)
        }

    @Test fun `bookmarking captures a snapshot of the loaded thread and toggles off again`() =
        runTest(dispatcher.scheduler) {
            val env = ThreadEnv()
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
            val env = ThreadEnv()
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()

            env.threads.result = DataResult.Success(env.details((100L..106L).map { ThreadEnv.post(it) }))
            vm.load(forceRefresh = true)
            dispatcher.scheduler.advanceUntilIdle()

            val rows = content(vm).rows
            assertEquals(8, rows.size)
            assertEquals(ThreadRow.Post(ThreadEnv.post(104)), rows[4])
            assertEquals(ThreadRow.NewPostsDivider(2), rows[5])
            assertEquals(ThreadRow.Post(ThreadEnv.post(105)), rows[6])

            vm.onDismissNewPostsDivider()
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(content(vm).rows.none { it is ThreadRow.NewPostsDivider })
        }

    @Test fun `404 during refresh keeps the loaded posts and shows the archived notice`() =
        runTest(dispatcher.scheduler) {
            val env = ThreadEnv()
            val vm = env.collectedVm(backgroundScope)
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
            val env = ThreadEnv()
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(vm.onExternalLink("https://example.com/page"))
            vm.onTrustDomain("https://example.com/page")
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue("example.com" in env.settings.state.value.trustedDomains)
            assertTrue(vm.onExternalLink("https://example.com/other"))
        }

    @Test fun `link confirmation off opens links immediately`() = runTest(dispatcher.scheduler) {
        val env = ThreadEnv()
        env.settings.state.value = Settings(confirmBeforeOpeningLinks = false)
        val vm = env.vm()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.onExternalLink("https://example.com/page"))
    }

    @Test fun `the preview path pushes, refocuses, pops and clears`() =
        runTest(dispatcher.scheduler) {
            val env = ThreadEnv()
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()

            vm.onOpenPreview(999) // not in the thread: nothing opens
            dispatcher.scheduler.advanceUntilIdle()
            assertNull(content(vm).preview)

            vm.onOpenPreview(101)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf(101L), content(vm).preview?.path)
            assertEquals(101L, content(vm).preview?.focus?.no)
            assertFalse(content(vm).preview!!.canGoBack)

            vm.onOpenPreview(101) // already on top: no duplicate step
            vm.onOpenPreview(103) // refocus: pushes
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf(101L, 103L), content(vm).preview?.path)
            assertEquals(103L, content(vm).preview?.focus?.no)
            assertTrue(content(vm).preview!!.canGoBack)

            vm.onClosePreview() // back arrow: one step
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf(101L), content(vm).preview?.path)

            vm.onOpenPreview(102)
            vm.onDismissPreview() // swiped away: the whole path
            dispatcher.scheduler.advanceUntilIdle()
            assertNull(content(vm).preview)
        }

    @Test fun `the preview sheet lists what the focus quotes above and what quotes it below`() =
        runTest(dispatcher.scheduler) {
            // 102 quotes 100 and 101; 103 and 104 quote 102; 104 also quotes 101.
            val posts = listOf(
                ThreadEnv.post(100).copy(isOp = true), ThreadEnv.post(101),
                ThreadEnv.post(102).copy(quotedPostNos = listOf(101, 100, 999)),
                ThreadEnv.post(103).copy(quotedPostNos = listOf(102)),
                ThreadEnv.post(104).copy(quotedPostNos = listOf(102, 101)),
            )
            val env = ThreadEnv(posts = posts, backlinks = PostGraph.backlinksOf(posts))
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()

            vm.onOpenPreview(102)
            dispatcher.scheduler.advanceUntilIdle()
            val sheet = content(vm).preview!!
            assertEquals(listOf(100L, 101L), sheet.parents.map { it.no }) // thread order, 999 dropped
            assertEquals(listOf(103L, 104L), sheet.replies.map { it.no })

            vm.onOpenPreview(101) // a "quoted by" tap: the post with its replies inline
            dispatcher.scheduler.advanceUntilIdle()
            val quoted = content(vm).preview!!
            assertEquals(emptyList<Long>(), quoted.parents.map { it.no })
            assertEquals(listOf(102L, 104L), quoted.replies.map { it.no })
            assertEquals(listOf(102L, 101L), quoted.path)
        }

    @Test fun `a 404 falls through to the archive and the copy names its source`() =
        runTest(dispatcher.scheduler) {
            val env = ThreadEnv()
            env.threads.result = DataResult.Failure(NetworkError.NotFound)
            env.threads.archived = DataResult.Success(
                env.details(listOf(ThreadEnv.post(100), ThreadEnv.post(101))).copy(archived = true, archive = ArchiveSource.DESU),
            )
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()

            val loaded = content(vm)
            assertEquals(listOf("live", "archive"), env.threads.asked)
            assertEquals(2, loaded.details.posts.size)
            assertTrue(loaded.archivedNotice)
            assertEquals("https://desuarchive.org/g/thread/100", loaded.archiveUrl)
        }

    @Test fun `a 404 with no archive copy stays a 404`() =
        runTest(dispatcher.scheduler) {
            val env = ThreadEnv()
            env.threads.result = DataResult.Failure(NetworkError.NotFound)
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(UiState.Error(NetworkError.NotFound), vm.uiState.value)
            assertEquals(listOf("live", "archive"), env.threads.asked)
        }

    @Test fun `a HIDE filter drops the post and a STUB filter collapses it until tapped`() =
        runTest(dispatcher.scheduler) {
            val env = ThreadEnv(posts = listOf(ThreadEnv.post(100).copy(isOp = true)) + (101L..104L).map(ThreadEnv::post))
            env.settings.state.value = Settings(
                filters = listOf(
                    Filter(id = "h", pattern = "match 102", action = FilterAction.HIDE),
                    Filter(id = "s", pattern = "other 103", action = FilterAction.STUB),
                ),
            )
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                listOf(ThreadRow.Post(ThreadEnv.post(100).copy(isOp = true)), ThreadRow.Post(ThreadEnv.post(101)),
                    ThreadRow.Filtered(103, "other 103"), ThreadRow.Post(ThreadEnv.post(104))),
                content(vm).rows,
            )
            assertEquals(2, content(vm).filteredCount)

            vm.onExpandFiltered(103)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(ThreadRow.Post(ThreadEnv.post(103)), content(vm).rows[2])
            assertEquals(2, content(vm).filteredCount)
        }

    @Test fun `the OP is never filtered and an empty filter list changes nothing`() =
        runTest(dispatcher.scheduler) {
            val env = ThreadEnv(posts = listOf(ThreadEnv.post(100).copy(isOp = true), ThreadEnv.post(101)))
            env.settings.state.value = Settings(
                filters = listOf(Filter(id = "h", pattern = "match 100", action = FilterAction.HIDE)),
            )
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()
            val loaded = content(vm)
            assertEquals(listOf(100L, 101L), loaded.rows.map { (it as ThreadRow.Post).post.no })
            assertEquals(0, loaded.filteredCount)
        }

    @Test fun `the vault copy comes before the archive and reads as offline`() =
        runTest(dispatcher.scheduler) {
            val env = ThreadEnv()
            env.threads.result = DataResult.Failure(NetworkError.NotFound)
            env.threads.archived = DataResult.Success(env.details((100L..104L).map(ThreadEnv::post)))
            env.vault.snapshot = env.details(listOf(ThreadEnv.post(100).copy(timeSeconds = 1_700_000_000L)))
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()

            val loaded = content(vm)
            assertEquals(listOf("live"), env.threads.asked)
            assertTrue(loaded.details.offlineCopy)
            assertEquals(1, loaded.details.posts.size)
            assertEquals(1_700_000_000_000L, loaded.offlineCopyAt)
            assertFalse(loaded.archivedNotice)
        }

    @Test fun `an offline error shows the vault copy but never asks the archive`() =
        runTest(dispatcher.scheduler) {
            val env = ThreadEnv()
            env.threads.result = DataResult.Failure(NetworkError.Offline)
            env.vault.snapshot = env.details(listOf(ThreadEnv.post(100)))
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(content(vm).details.offlineCopy)

            env.vault.snapshot = null
            env.threads.asked.clear()
            val vm2 = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(UiState.Error(NetworkError.Offline), vm2.uiState.value)
            assertEquals(listOf("live"), env.threads.asked)
        }
}

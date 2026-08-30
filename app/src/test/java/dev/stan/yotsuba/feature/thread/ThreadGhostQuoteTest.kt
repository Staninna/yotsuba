package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.domain.model.ArchiveSource
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.QuoteTapAction
import dev.stan.yotsuba.domain.model.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** A cross-thread quote or a deadlink opens the quoted post as a ghost in the preview sheet. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadGhostQuoteTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val env = ThreadEnv()

    /** /b/200: 201 quotes 200, 202 quotes 201. */
    private val other = env.details(
        listOf(
            ThreadEnv.post(200).copy(board = "b", isOp = true),
            ThreadEnv.post(201).copy(board = "b", quotedPostNos = listOf(200)),
            ThreadEnv.post(202).copy(board = "b", quotedPostNos = listOf(201)),
        ),
        board = "b", threadNo = 200,
    )

    private fun sheet(vm: ThreadViewModel) = content(vm).preview
    private fun post(vm: ThreadViewModel) = sheet(vm) as PreviewSheet.Post

    @Test fun `a cross-thread quote opens a live ghost with its own parents and replies`() =
        runTest(dispatcher.scheduler) {
            env.threads.byThread["b" to 200L] = DataResult.Success(other)
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()
            env.threads.asked.clear()

            assertTrue(vm.onCrossThreadQuoteTap("b", 200, 201))
            dispatcher.scheduler.advanceUntilIdle()

            val ghost = post(vm)
            assertEquals(201L, ghost.focus.no)
            assertEquals(Ghost("b", 200, GhostSource.Live), ghost.ghost)
            assertEquals(listOf(200L), ghost.parents.map { it.no })
            assertEquals(listOf(202L), ghost.replies.map { it.no })
            assertEquals(listOf(201L), ghost.path)
            assertEquals(listOf("live"), env.threads.asked)
            assertNull(vm.scrollTarget.value) // the reader's place is kept
        }

    @Test fun `the jump setting hands a cross-thread quote back to the screen`() =
        runTest(dispatcher.scheduler) {
            env.settings.state.value = Settings(quoteTap = QuoteTapAction.JUMP)
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(vm.onCrossThreadQuoteTap("b", 200, 201))
            dispatcher.scheduler.advanceUntilIdle()
            assertNull(sheet(vm))
        }

    @Test fun `a quote to a whole thread previews its OP`() = runTest(dispatcher.scheduler) {
        env.threads.byThread["b" to 200L] = DataResult.Success(other)
        val vm = env.collectedVm(backgroundScope)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onOpenGhost("b", 200, null)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(200L, post(vm).focus.no)
    }

    @Test fun `a quote inside a ghost post stays in the ghost's thread and is served from the cache`() =
        runTest(dispatcher.scheduler) {
            env.threads.byThread["b" to 200L] = DataResult.Success(other)
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()
            vm.onOpenGhost("b", 200, 201)
            dispatcher.scheduler.advanceUntilIdle()
            env.threads.asked.clear()

            vm.onOpenPreview(202) // a quotelink in the ghost: /b/202, not this thread's 102
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf(201L, 202L), post(vm).path)
            assertEquals("b", post(vm).focus.board)
            assertEquals(emptyList<String>(), env.threads.asked)

            vm.onClosePreview()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(201L, post(vm).focus.no)

            vm.onDismissPreview()
            dispatcher.scheduler.advanceUntilIdle()
            vm.onOpenPreview(102) // sheet closed: back to this thread
            dispatcher.scheduler.advanceUntilIdle()
            assertNull(post(vm).ghost)
        }

    @Test fun `a deadlink is read from the saved copy of this thread, never live`() =
        runTest(dispatcher.scheduler) {
            env.vault.snapshot = env.details(listOf(ThreadEnv.post(100), ThreadEnv.post(99)))
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()
            env.threads.asked.clear()

            vm.onDeadlinkTap(99)
            dispatcher.scheduler.advanceUntilIdle()
            val ghost = post(vm)
            assertEquals(99L, ghost.focus.no)
            assertEquals(Ghost("g", 100, GhostSource.Saved), ghost.ghost)
            assertEquals(emptyList<String>(), env.threads.asked)
        }

    @Test fun `a deadlink with no saved copy goes to the archive and names it`() =
        runTest(dispatcher.scheduler) {
            env.threads.archived = DataResult.Success(
                env.details(listOf(ThreadEnv.post(100), ThreadEnv.post(99))).copy(archive = ArchiveSource.DESU),
            )
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()
            env.threads.asked.clear()

            vm.onDeadlinkTap(99)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(Ghost("g", 100, GhostSource.Archived(ArchiveSource.DESU)), post(vm).ghost)
            assertEquals(listOf("archive"), env.threads.asked)
        }

    @Test fun `a post nobody has reads as missing, and the sheet loads meanwhile`() =
        runTest(dispatcher.scheduler) {
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()

            vm.onDeadlinkTap(99)
            // The fakes answer without suspending, so the loading state is caught on the session.
            assertEquals(GhostState.Loading, vm.session.value.ghosts[ThreadKey("g", 100)])

            dispatcher.scheduler.advanceUntilIdle()
            val missing = sheet(vm) as PreviewSheet.Missing
            assertEquals(NetworkError.NotFound, missing.error)
            assertEquals(Ghost("g", 100, null), missing.ghost)
            assertEquals(listOf(99L), missing.path)
        }

    @Test fun `a failed lookup keeps a copy already held for the posts it does have`() =
        runTest(dispatcher.scheduler) {
            env.threads.byThread["b" to 200L] = DataResult.Success(other)
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()
            vm.onOpenGhost("b", 200, 201)
            dispatcher.scheduler.advanceUntilIdle()

            vm.onOpenPreview(999) // not in /b/200 anywhere
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(sheet(vm) is PreviewSheet.Missing)

            vm.onClosePreview()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(201L, post(vm).focus.no)
        }
}

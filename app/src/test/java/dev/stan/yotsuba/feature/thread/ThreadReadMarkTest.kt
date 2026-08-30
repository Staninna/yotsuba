package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.fake.MainDispatcherRule
import dev.stan.yotsuba.feature.thread.ThreadEnv.Companion.post
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The bottom-visible post becomes the read mark, written once the list has settled. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadReadMarkTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test fun `a fling through the thread writes the read mark once, at the row it stopped on`() =
        runTest(dispatcher.scheduler) {
            val env = ThreadEnv(posts = (100L..110L).map(::post))
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()

            // One range report per row passed, faster than the settle time.
            (1..10).forEach { last ->
                vm.onVisiblePostsChanged(0, last)
                dispatcher.scheduler.advanceTimeBy(50)
            }
            assertEquals(emptyList<Long>(), env.bookmarks.seen)
            assertEquals(null, env.history.readMark)

            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf(110L), env.bookmarks.seen)
            assertEquals(110L, env.history.readMark)
        }

    @Test fun `a pause between two scrolls writes each stopping point`() = runTest(dispatcher.scheduler) {
        val env = ThreadEnv(posts = (100L..110L).map(::post))
        val vm = env.collectedVm(backgroundScope)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onVisiblePostsChanged(0, 3)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onVisiblePostsChanged(0, 7)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(103L, 107L), env.bookmarks.seen)
    }

    @Test fun `scrolling back up never lowers the mark`() = runTest(dispatcher.scheduler) {
        val env = ThreadEnv(posts = (100L..110L).map(::post))
        val vm = env.collectedVm(backgroundScope)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onVisiblePostsChanged(0, 7)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onVisiblePostsChanged(0, 2)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(107L), env.bookmarks.seen)
        assertEquals(107L, env.history.readMark)
    }
}

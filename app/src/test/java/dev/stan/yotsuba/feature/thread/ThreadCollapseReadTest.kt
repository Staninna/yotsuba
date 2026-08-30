package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.fake.FakeSettings
import dev.stan.yotsuba.feature.thread.ThreadEnv.Companion.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** A watched thread with unread replies opens with the read part folded under the OP. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadCollapseReadTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun env(readMark: Long? = 102, watched: Boolean = true, collapse: Boolean = true) =
        ThreadEnv(
            posts = (100L..104L).map(::post),
            settings = FakeSettings(Settings(collapseReadPosts = collapse)),
        ).apply {
            history.readMark = readMark
            bookmarks.bookmarkedFlow.value = watched
        }

    private fun shape(vm: ThreadViewModel) = content(vm).rows.map {
        when (it) {
            is ThreadRow.Post -> it.post.no.toString()
            is ThreadRow.EarlierPosts -> "earlier:${it.count}"
            else -> it::class.simpleName!!
        }
    }

    @Test fun `read posts fold under the OP and a tap unfolds them`() = runTest(dispatcher.scheduler) {
        val vm = env().collectedVm(backgroundScope)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("100", "earlier:2", "103", "104"), shape(vm))

        vm.onExpandEarlier()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("100", "101", "102", "103", "104"), shape(vm))
    }

    @Test fun `nothing folds without a bookmark, without the setting, or with nothing unread`() =
        runTest(dispatcher.scheduler) {
            val all = listOf("100", "101", "102", "103", "104")
            listOf(env(watched = false), env(collapse = false), env(readMark = 104), env(readMark = null)).forEach { e ->
                val vm = e.collectedVm(backgroundScope)
                dispatcher.scheduler.advanceUntilIdle()
                assertEquals(all, shape(vm))
            }
        }

    @Test fun `a jump into the folded run unfolds it`() = runTest(dispatcher.scheduler) {
        val vm = env().collectedVm(backgroundScope)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onJumpToPost(101)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("100", "101", "102", "103", "104"), shape(vm))
    }

    @Test fun `a refresh's new-posts divider lands after the folded run`() = runTest(dispatcher.scheduler) {
        val e = env()
        val vm = e.collectedVm(backgroundScope)
        dispatcher.scheduler.advanceUntilIdle()
        // The read mark rises to 104 on screen, so the next refresh's divider would sit at 104.
        e.threads.result = dev.stan.yotsuba.domain.model.DataResult.Success(e.details((100L..106L).map(::post)))
        vm.load(forceRefresh = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("100", "earlier:2", "103", "104", "NewPostsDivider", "105", "106"), shape(vm))
    }
}

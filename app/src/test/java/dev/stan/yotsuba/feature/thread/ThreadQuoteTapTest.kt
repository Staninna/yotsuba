package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.domain.model.PostSegment
import dev.stan.yotsuba.domain.model.PostText
import dev.stan.yotsuba.domain.model.QuoteTapAction
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.fake.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** A quotelink tap follows [Settings.quoteTap]; a long-press does the other thing. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadQuoteTapTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val env = ThreadEnv(posts = (100L..104L).map(::post))

    private fun previewNos(vm: ThreadViewModel) = content(vm).preview?.path.orEmpty()

    @Test fun `popover setting opens the preview on tap and jumps on long-press`() = runTest(dispatcher.scheduler) {
        env.settings.state.value = Settings(quoteTap = QuoteTapAction.POPOVER)
        val vm = env.collectedVm(backgroundScope)
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
        env.settings.state.value = Settings(quoteTap = QuoteTapAction.JUMP)
        val vm = env.collectedVm(backgroundScope)
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

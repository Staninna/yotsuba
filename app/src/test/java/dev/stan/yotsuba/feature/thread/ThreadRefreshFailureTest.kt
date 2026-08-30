package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.core.text.PostSegment
import dev.stan.yotsuba.core.text.PostText
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.domain.model.ThreadPost
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

/** A refresh that fails must not take the loaded thread off screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadRefreshFailureTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val env = ThreadEnv(posts = (100L..104L).map(::post))

    @Test fun `failed poll after a successful load keeps the posts and reports the error`() =
        runTest(dispatcher.scheduler) {
            val vm = env.collectedVm(backgroundScope)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(5, content(vm).details.posts.size)

            env.threads.result = DataResult.Failure(NetworkError.Offline)
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
        env.threads.result = DataResult.Failure(NetworkError.Timeout)
        val vm = env.collectedVm(backgroundScope)
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

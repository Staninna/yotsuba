package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.domain.model.BoardProfile
import dev.stan.yotsuba.domain.model.FontSize
import dev.stan.yotsuba.domain.model.LineSpacing
import dev.stan.yotsuba.fake.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The board profile's text size and line spacing have to reach the screen, which is what
 * re-provides post typography from them; a state that drops them makes the chips dead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadBoardProfileTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val env = ThreadEnv()

    @Test fun `the board profile's typography reaches the thread state`() = runTest(dispatcher.scheduler) {
        env.settings.state.value = env.settings.state.value.copy(
            fontSize = FontSize.SMALL,
            lineSpacing = LineSpacing.COMPACT,
            boardProfiles = mapOf("g" to BoardProfile(fontSize = FontSize.LARGE, lineSpacing = LineSpacing.RELAXED)),
        )
        val vm = env.collectedVm(backgroundScope)
        dispatcher.scheduler.advanceUntilIdle()

        val content = (vm.uiState.value as UiState.Success).data
        assertEquals(FontSize.LARGE, content.fontSize)
        assertEquals(LineSpacing.RELAXED, content.lineSpacing)
    }
}

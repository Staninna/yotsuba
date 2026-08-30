package dev.stan.yotsuba

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.di.FakeSettingsRepository
import dev.stan.yotsuba.di.TestSeed
import javax.inject.Inject
import org.junit.Test

@HiltAndroidTest
class RevealSpoilersFlowTest : FlowTest() {

    @Inject
    lateinit var settings: FakeSettingsRepository

    /** With "Reveal all spoilers" on, the first tap opens the viewer — no reveal step. */
    @Test
    fun revealAllSpoilersOn_singleTapOpensViewer() {
        settings.state.value = settings.state.value.copy(revealAllSpoilers = true)

        composeRule.openSeededThread()
        composeRule.waitForContentDescription(TestSeed.SPOILER_FILENAME)
        composeRule.onNodeWithContentDescription(
            TestSeed.SPOILER_FILENAME, substring = true, ignoreCase = true,
        ).performClick()
        composeRule.waitForContentDescription("Close viewer")
    }
}

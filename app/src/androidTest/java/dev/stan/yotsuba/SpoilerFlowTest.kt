package dev.stan.yotsuba

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.di.TestSeed
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class SpoilerFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun spoileredThumbnail_firstTapReveals_secondTapOpensViewer() {
        composeRule.openSeededThread()
        composeRule.waitForText(TestSeed.SPOILER_REPLY_TEXT)
        composeRule.waitForContentDescription(TestSeed.SPOILER_FILENAME)

        // First tap only reveals the image — the viewer must NOT open.
        composeRule.onNodeWithContentDescription(
            TestSeed.SPOILER_FILENAME, substring = true, ignoreCase = true,
        ).performClick()
        composeRule.waitForIdle()
        assertTrue(
            "Viewer must stay closed on the reveal tap",
            composeRule.onAllNodesWithContentDescription("Close viewer")
                .fetchSemanticsNodes().isEmpty(),
        )

        // Second tap opens the media viewer.
        composeRule.onNodeWithContentDescription(
            TestSeed.SPOILER_FILENAME, substring = true, ignoreCase = true,
        ).performClick()
        composeRule.waitForContentDescription("Close viewer")
    }
}

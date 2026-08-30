package dev.stan.yotsuba

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

@HiltAndroidTest
class ThreadSearchFlowTest : FlowTest() {

    @Test
    fun searchInThread_countsMatches_andStepsBetweenThem() {
        composeRule.openSeededThread()

        // Search lives in the top-bar overflow menu.
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.waitForText("Search in thread")
        composeRule.clickText("Search in thread")

        // The search bar is the only text field on screen.
        composeRule.waitForText("Search in thread") // now the field's placeholder
        composeRule.onNode(hasSetTextAction()).performTextInput("seeded")

        // "seeded" appears in the OP and the first reply: counter starts at 1/2.
        composeRule.waitForText("1/2", substring = false)

        // Step forward wraps through the matches.
        composeRule.onNodeWithContentDescription("Next match").performClick()
        composeRule.waitForText("2/2", substring = false)
        composeRule.onNodeWithContentDescription("Next match").performClick()
        composeRule.waitForText("1/2", substring = false)

        // And backward.
        composeRule.onNodeWithContentDescription("Previous match").performClick()
        composeRule.waitForText("2/2", substring = false)
    }
}

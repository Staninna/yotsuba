package dev.stan.yotsuba

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.di.TestSeed
import org.junit.Test

@HiltAndroidTest
class HistoryFlowTest : FlowTest() {

    @Test
    fun visitingThread_recordsHistoryEntry() {
        composeRule.openSeededThread()

        // Leave the thread, then open the Recent segment of the Threads tab.
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForText("Threads")
        composeRule.clickText("Threads")
        composeRule.waitForText("Recent")
        composeRule.clickText("Recent")

        // The visited thread shows up as a history entry.
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
    }
}

package dev.stan.yotsuba

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.di.TestSeed
import org.junit.Test

@HiltAndroidTest
class HistoryClearFlowTest : FlowTest() {

    @Test
    fun clearAll_asksForConfirmation_thenEmptiesHistory() {
        composeRule.openSeededThread()
        composeRule.openThreadsTab("Recent")
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)

        composeRule.onNodeWithContentDescription("Clear all").performClick()
        composeRule.waitForText("Clear all history?")

        // Exact match hits the dialog's confirm button, not its title.
        composeRule.clickText("Clear all", substring = false)
        composeRule.waitForText("No history")
    }
}

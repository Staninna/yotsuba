package dev.stan.yotsuba

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.di.TestSeed
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class HistoryClearFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun clearAll_asksForConfirmation_thenEmptiesHistory() {
        composeRule.openSeededThread()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForText("Threads")
        composeRule.clickText("Threads")
        composeRule.waitForText("Recent")
        composeRule.clickText("Recent")
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)

        composeRule.onNodeWithContentDescription("Clear all").performClick()
        composeRule.waitForText("Clear all history?")

        // Exact match hits the dialog's confirm button, not its title.
        composeRule.onNodeWithText("Clear all").performClick()
        composeRule.waitForText("No history")
    }
}

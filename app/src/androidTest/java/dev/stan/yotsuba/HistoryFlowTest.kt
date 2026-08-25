package dev.stan.yotsuba

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.di.TestSeed
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class HistoryFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun visitingThread_recordsHistoryEntry() {
        composeRule.openSeededThread()

        // Leave the thread, then open the History tab.
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForText("History")
        composeRule.clickText("History")

        // The visited thread shows up as a history entry.
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
    }
}

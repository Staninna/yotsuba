package dev.stan.yotsuba

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.di.TestSeed
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class HiddenThreadFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun longPressHidesThread_undoBringsItBack() {
        composeRule.waitForText(TestSeed.BOARD_TITLE)
        composeRule.clickText(TestSeed.BOARD_TITLE)
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)

        // Long-press the catalog card to hide the thread.
        composeRule.onNodeWithText(TestSeed.THREAD_SUBJECT, substring = true, ignoreCase = true)
            .performTouchInput { longClick() }
        composeRule.waitForText("Thread hidden")
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithText(TestSeed.THREAD_SUBJECT, substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        }

        // Undo from the snackbar restores it.
        composeRule.clickText("Undo")
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
    }
}

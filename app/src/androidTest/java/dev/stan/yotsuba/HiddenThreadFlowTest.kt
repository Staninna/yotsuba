package dev.stan.yotsuba

import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.di.TestSeed
import org.junit.Test

@HiltAndroidTest
class HiddenThreadFlowTest : FlowTest() {

    @Test
    fun longPressHidesThread_undoBringsItBack() {
        composeRule.openBoardsTab()
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

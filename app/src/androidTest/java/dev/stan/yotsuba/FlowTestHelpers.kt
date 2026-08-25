package dev.stan.yotsuba

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.stan.yotsuba.di.TestSeed

const val UI_TIMEOUT_MS = 10_000L

fun ComposeTestRule.waitForText(text: String, substring: Boolean = true) {
    waitUntil(UI_TIMEOUT_MS) {
        onAllNodesWithText(text, substring = substring, ignoreCase = true)
            .fetchSemanticsNodes().isNotEmpty()
    }
}

fun ComposeTestRule.waitForContentDescription(description: String) {
    waitUntil(UI_TIMEOUT_MS) {
        onAllNodesWithContentDescription(description, substring = true, ignoreCase = true)
            .fetchSemanticsNodes().isNotEmpty()
    }
}

fun ComposeTestRule.clickText(text: String) {
    onNodeWithText(text, substring = true, ignoreCase = true).performClick()
}

/** Boards → catalog → thread, entirely through the real nav graph. */
fun ComposeTestRule.openSeededThread() {
    waitForText(TestSeed.BOARD_TITLE)
    clickText(TestSeed.BOARD_TITLE)
    waitForText(TestSeed.THREAD_SUBJECT)
    clickText(TestSeed.THREAD_SUBJECT)
    waitForText(TestSeed.OP_TEXT)
}

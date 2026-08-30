package dev.stan.yotsuba

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import dagger.hilt.android.testing.HiltAndroidRule
import dev.stan.yotsuba.di.TestSeed
import org.junit.Before
import org.junit.Rule

const val UI_TIMEOUT_MS = 10_000L

/**
 * Shared rules for every instrumented flow test: Hilt must be set up (order 0) before the
 * activity launches (order 1). Subclasses still carry their own @HiltAndroidTest annotation.
 */
abstract class FlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> =
        createAndroidComposeRule<MainActivity>()

    @Before
    fun injectHilt() {
        hiltRule.inject()
    }
}

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

/** Home is the start destination; the board list is one tab over. Exact match: "Pick boards" also contains it. */
fun ComposeTestRule.openBoardsTab() {
    onNodeWithText("Boards", substring = false).performClick()
}

/** Home → boards → catalog → thread, entirely through the real nav graph. */
fun ComposeTestRule.openSeededThread() {
    openBoardsTab()
    waitForText(TestSeed.BOARD_TITLE)
    clickText(TestSeed.BOARD_TITLE)
    waitForText(TestSeed.THREAD_SUBJECT)
    clickText(TestSeed.THREAD_SUBJECT)
    waitForText(TestSeed.OP_TEXT)
}

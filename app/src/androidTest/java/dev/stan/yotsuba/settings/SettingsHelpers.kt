package dev.stan.yotsuba.settings

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitUntilTrue

/**
 * Taps the row, chip or button labelled [label], scrolling it into view first: every
 * settings section is one long scrolling column, and a tap off-screen is an error.
 */
fun ComposeTestRule.tapRow(label: String) {
    waitForText(label, substring = false)
    nodeWithText(label, substring = false).performScrollTo().performClick()
}

/** Taps [label], then waits for the settings the app persisted to read [expected]. */
fun <T> FlowTest.flip(label: String, expected: T, read: (Settings) -> T) {
    composeRule.tapRow(label)
    composeRule.waitUntilTrue { read(fakes.settings.state.value) == expected }
}

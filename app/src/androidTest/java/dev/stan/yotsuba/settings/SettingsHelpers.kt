package dev.stan.yotsuba.settings

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitUntilTrue

/**
 * Taps the row, chip or button labelled [label]. A settings section is one long scrolling
 * column, so anything inside one is scrolled into view first; a dialog's buttons have no
 * scroll parent and are already on screen.
 */
fun ComposeTestRule.tapRow(label: String) {
    waitForText(label, substring = false)
    val matcher = hasText(label, substring = false, ignoreCase = true)
    if (onAllNodes(matcher and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) {
        onNode(matcher).performScrollTo()
    }
    onNode(matcher).performClick()
}

/** Taps [label], then waits for the settings the app persisted to read [expected]. */
fun <T> FlowTest.flip(label: String, expected: T, read: (Settings) -> T) {
    composeRule.tapRow(label)
    composeRule.waitUntilTrue { read(fakes.settings.state.value) == expected }
}

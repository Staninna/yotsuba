package dev.stan.yotsuba

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dev.stan.yotsuba.di.TestSeed

const val UI_TIMEOUT_MS = 10_000L

/*
 * Selectors. Every screen is reached through the real navigation graph, and nodes are found
 * by the text or content description the user sees, so a test reads like the flow it checks.
 * Matching is case-insensitive and by substring unless told otherwise.
 */

fun ComposeTestRule.waitForText(text: String, substring: Boolean = true) {
    waitUntil(UI_TIMEOUT_MS) {
        onAllNodesWithText(text, substring = substring, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
    }
}

/**
 * Waits for [text] to be on screen, not merely composed. A lazy list composes a little beyond
 * the viewport, so a node existing says nothing about the user seeing it, and a scroll that
 * has not run yet looks the same as one that has.
 */
fun ComposeTestRule.waitForTextDisplayed(text: String, substring: Boolean = true) {
    waitUntil(UI_TIMEOUT_MS) {
        val screen = onRoot().fetchSemanticsNode().boundsInRoot
        onAllNodesWithText(text, substring = substring, ignoreCase = true)
            .fetchSemanticsNodes()
            .any { it.boundsInRoot.overlaps(screen) }
    }
}

fun ComposeTestRule.waitForTextGone(text: String, substring: Boolean = true) {
    waitUntil(UI_TIMEOUT_MS) {
        onAllNodesWithText(text, substring = substring, ignoreCase = true).fetchSemanticsNodes().isEmpty()
    }
}

fun ComposeTestRule.waitForContentDescription(description: String, substring: Boolean = true) {
    waitUntil(UI_TIMEOUT_MS) {
        onAllNodesWithContentDescription(description, substring = substring, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
    }
}

fun ComposeTestRule.waitForContentDescriptionGone(description: String, substring: Boolean = true) {
    waitUntil(UI_TIMEOUT_MS) {
        onAllNodesWithContentDescription(description, substring = substring, ignoreCase = true).fetchSemanticsNodes().isEmpty()
    }
}

/** Waits for a fake's state to settle, for assertions that go behind the screen. */
fun ComposeTestRule.waitUntilTrue(condition: () -> Boolean) = waitUntil(UI_TIMEOUT_MS, condition)

fun ComposeTestRule.hasText(text: String, substring: Boolean = true): Boolean =
    onAllNodesWithText(text, substring = substring, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()

fun ComposeTestRule.hasContentDescription(description: String, substring: Boolean = true): Boolean =
    onAllNodesWithContentDescription(description, substring = substring, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()

fun ComposeTestRule.clickText(text: String, substring: Boolean = true) {
    onNodeWithText(text, substring = substring, ignoreCase = true).performClick()
}

/** Waits for the text, then taps it. Most of a flow test is this. */
fun ComposeTestRule.tap(text: String, substring: Boolean = true) {
    waitForText(text, substring)
    clickText(text, substring)
}

fun ComposeTestRule.clickContentDescription(description: String, substring: Boolean = false) {
    onNodeWithContentDescription(description, substring = substring, ignoreCase = true).performClick()
}

/** Waits for the icon, then taps it. */
fun ComposeTestRule.tapIcon(description: String, substring: Boolean = false) {
    waitForContentDescription(description, substring)
    clickContentDescription(description, substring)
}

fun ComposeTestRule.nodeWithText(text: String, substring: Boolean = true): SemanticsNodeInteraction =
    onNodeWithText(text, substring = substring, ignoreCase = true)

fun ComposeTestRule.nodeWithContentDescription(description: String, substring: Boolean = false): SemanticsNodeInteraction =
    onNodeWithContentDescription(description, substring = substring, ignoreCase = true)

/**
 * An icon inside the clickable row that carries [rowText]: the favourite star of one board,
 * the overflow of one thread. Beats indexing into `onAllNodes`, which follows list order.
 */
fun ComposeTestRule.iconInRow(rowText: String, description: String): SemanticsNodeInteraction =
    onNode(inRow(rowText, description), useUnmergedTree = true)

/** The matcher behind [iconInRow], for `onAllNodes(inRow(...), useUnmergedTree = true)` when a row repeats. */
fun inRow(rowText: String, description: String): SemanticsMatcher =
    hasContentDescription(description, substring = false, ignoreCase = true) and
        hasAnyAncestor(hasClickAction() and hasAnyDescendant(hasText(rowText, substring = true, ignoreCase = true)))

/** The one text field on screen (a search bar, a dialog's input). */
fun ComposeTestRule.textField(): SemanticsNodeInteraction = onNode(hasSetTextAction())

fun ComposeTestRule.typeInField(text: String) {
    textField().performTextInput(text)
}

fun ComposeTestRule.clearField() {
    textField().performTextClearance()
}

/*
 * Navigation. Labels are exact where a substring would also hit another node ("Boards" is
 * inside "Pick boards", "Saved" inside "Saved media").
 */

/**
 * A bottom-bar item by its label, told from a screen title of the same word by its click
 * action: the Home tab and an unloaded Home screen both say "Home", and the Boards tab and
 * the Boards screen both say "Boards".
 */
fun ComposeTestRule.tab(label: String): SemanticsNodeInteraction = onNode(tabMatcher(label))

/** Top level, because this file's own `hasText` is a Boolean check on the rule, not a matcher. */
private fun tabMatcher(label: String): SemanticsMatcher =
    hasText(label, substring = false) and hasClickAction()

fun ComposeTestRule.tapTab(label: String) {
    waitForText(label, substring = false)
    tab(label).performClick()
}

fun ComposeTestRule.openHomeTab() = tapTab("Home")
fun ComposeTestRule.openBoardsTab() = tapTab("Boards")
fun ComposeTestRule.openVaultTab() = tapTab("Saved")

/**
 * Opens the Threads tab and waits for [segment] ("Watched" or "Recent") to show, selecting
 * it unless [select] is false, for the default segment that is already showing.
 */
fun ComposeTestRule.openThreadsTab(segment: String = "Watched", select: Boolean = segment != "Watched") {
    tapTab("Threads")
    waitForText(segment)
    if (select) clickText(segment)
}

/** Backs out of a pushed screen with the top bar's back arrow. */
fun ComposeTestRule.goBack() = tapIcon("Back")

/** Backs out of pushed screens until the bottom bar is showing again. */
fun ComposeTestRule.backToTabs() {
    repeat(5) {
        if (hasText("Threads", substring = false)) return
        goBack()
        waitForIdle()
    }
    waitForText("Threads", substring = false)
}

/** Boards tab, then the board named [title], and waits for the catalog's first thread. */
fun ComposeTestRule.openCatalog(title: String = TestSeed.BOARD_TITLE, firstThread: String = TestSeed.THREAD_SUBJECT) {
    openBoardsTab()
    tap(title)
    waitForText(firstThread)
}

/** Home → boards → catalog → thread, entirely through the real nav graph. */
fun ComposeTestRule.openSeededThread() {
    openCatalog()
    tap(TestSeed.THREAD_SUBJECT)
    waitForText(TestSeed.OP_TEXT)
}

/** Any thread by board title and subject; waits for [opText] once inside. */
fun ComposeTestRule.openThread(boardTitle: String, subject: String, opText: String) {
    openCatalog(boardTitle, subject)
    tap(subject)
    waitForText(opText)
}

/** The settings index, from the gear on the Home tab. */
fun ComposeTestRule.openSettings() = tapIcon("Settings")

/** Settings index → the section titled [section]; waits for [firstRow] to appear. */
fun ComposeTestRule.openSettingsSection(section: String, firstRow: String) {
    openSettings()
    tap(section, substring = false)
    waitForText(firstRow)
}

/** The seeded reply's thumbnail, then the viewer's close button as proof it opened. */
fun ComposeTestRule.openSeededViewer() {
    openSeededThread()
    tapIcon(TestSeed.MEDIA_FILENAME, substring = true)
    waitForContentDescription("Close viewer")
}

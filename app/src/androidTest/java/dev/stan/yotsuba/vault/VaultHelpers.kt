package dev.stan.yotsuba.vault

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import dev.stan.yotsuba.clickContentDescription
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.hasContentDescription
import dev.stan.yotsuba.hasText as showsText
import dev.stan.yotsuba.nodeWithContentDescription
import dev.stan.yotsuba.openVaultTab
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals

/*
 * The vault's own selectors. Segmented rows share their labels with the bottom bar
 * ("Threads") and with grid badges ("With sound"), so segments are picked by role; dialog
 * buttons share their text with the dialog's title, so they are picked by click action.
 */

/** The Saved tab, which opens on the Recent grid. */
fun ComposeTestRule.openVault() {
    openVaultTab()
    waitForText("Saved media")
}

/** One segment of a segmented row: the only radio-button role in the app. */
fun ComposeTestRule.segment(label: String): SemanticsNodeInteraction = onNode(
    hasText(label, substring = false, ignoreCase = true) and
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
)

fun ComposeTestRule.tapSegment(label: String) {
    waitForText(label, substring = false)
    segment(label).performClick()
}

/** A clickable carrying exactly [text]: a dialog button whose title repeats its label. */
fun ComposeTestRule.button(text: String): SemanticsNodeInteraction =
    onNode(hasText(text, substring = false, ignoreCase = true) and hasClickAction())

/** Browse mode, then the board row titled [board] ("/g/", "Imported", "Unsorted"). */
fun ComposeTestRule.openVaultBoard(board: String) {
    tapSegment("Browse")
    tap(board, substring = false)
}

/** Browse → /g/ → the seeded thread's grid, proven by the file its list row does not show. */
fun ComposeTestRule.openSeededVaultThread() {
    openVault()
    openVaultBoard("/${TestSeed.BOARD}/")
    tap(TestSeed.THREAD_SUBJECT)
    waitForContentDescription(VaultSeed.spoilerImage.displayName)
}

fun ComposeTestRule.longPressText(text: String) {
    waitForText(text)
    onNode(hasText(text, substring = true, ignoreCase = true)).performTouchInput { longClick() }
}

/** Long-presses the grid cell for [name] and waits for its sheet's title. */
fun ComposeTestRule.openEntrySheet(name: String) {
    waitForContentDescription(name)
    nodeWithContentDescription(name).performTouchInput { longClick() }
    waitForText(name, substring = false)
}

/** Taps the grid cell for [name] and waits for the viewer's chrome. */
fun ComposeTestRule.openVaultViewer(name: String) {
    waitForContentDescription(name)
    clickContentDescription(name)
    waitForContentDescription("Close viewer")
}

/** The chrome hides itself after a few seconds; a tap on the page brings it back. */
fun ComposeTestRule.showViewerChrome() {
    if (!hasContentDescription("Close viewer")) onRoot().performTouchInput { click(center) }
    waitForContentDescription("Close viewer")
}

/** The file name in the viewer's top bar, which only exists while the chrome shows. */
fun ComposeTestRule.waitForViewerTitle(name: String) {
    showViewerChrome()
    waitForText(name, substring = false)
}

/** One of [names] is in the viewer's title; which one a shuffle picked is random. */
fun ComposeTestRule.waitForViewerTitleIn(names: Collection<String>) {
    showViewerChrome()
    waitUntilTrue { names.any { showsText(it, substring = false) } }
}

/**
 * The page under the viewer's chrome, told apart from the grid cell of the same file (still
 * composed underneath) by being as wide as the screen.
 */
fun ComposeTestRule.viewerPage(name: String): SemanticsNodeInteraction {
    val rootWidth = onRoot().fetchSemanticsNode().size.width
    return onNode(
        hasContentDescription(name, substring = false, ignoreCase = true) and
            SemanticsMatcher("fills the width") { it.size.width >= rootWidth },
    )
}

private val vaultThumbnail = SemanticsMatcher("a seeded vault thumbnail") { node ->
    node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it in VaultSeed.names } == true
}

/** Display names of the thumbnails on screen, in reading order. */
fun ComposeTestRule.gridOrder(): List<String> = onAllNodes(vaultThumbnail).fetchSemanticsNodes()
    .sortedWith(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))
    .map { it.config[SemanticsProperties.ContentDescription].first { name -> name in VaultSeed.names } }

fun ComposeTestRule.waitForGridOrder(expected: List<String>) {
    waitUntilTrue { gridOrder() == expected }
    assertEquals(expected, gridOrder())
}

/** Waits for the delete confirmation and accepts it. */
fun ComposeTestRule.confirmDelete() {
    waitForText("Delete file?")
    button("Delete").performClick()
}

/*
 * A bottom sheet lies over the explorer, and the grid underneath keeps its nodes: a
 * thumbnail's file name matches in both. Everything below is scoped by a text only the
 * open sheet shows, so a sheet's own copy of a file wins.
 */

/** A node inside the sheet titled [titled], by the content description [cd]. */
fun ComposeTestRule.inSheet(titled: String, cd: String): SemanticsNodeInteraction = onNode(
    hasContentDescription(cd, substring = false, ignoreCase = true) and
        hasAnyAncestor(hasAnyDescendant(hasText(titled, substring = false, ignoreCase = true))),
)

/**
 * Scrolls the sheet titled [titled] until [text] is composed. A sheet long enough to need
 * this only composes the rows near the top, so its buttons cannot be tapped until it does.
 */
fun ComposeTestRule.scrollSheetTo(titled: String, text: String) {
    waitForText(titled, substring = false)
    onNode(hasScrollAction() and hasAnyDescendant(hasText(titled, substring = false, ignoreCase = true)))
        .performScrollToNode(hasText(text, substring = true, ignoreCase = true))
    waitForText(text)
}

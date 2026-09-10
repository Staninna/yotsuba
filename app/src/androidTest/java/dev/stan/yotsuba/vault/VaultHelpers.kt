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
import androidx.compose.ui.test.swipeUp
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
import org.junit.Assert.assertTrue

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

/**
 * Taps a chrome button, bringing the chrome back first. The chrome hides itself three
 * seconds after the last touch, which can be mid-tap, so a lost node is retried rather
 * than failing the test over the timer.
 */
fun ComposeTestRule.tapViewerIcon(description: String) {
    repeat(3) {
        showViewerChrome()
        if (runCatching { clickContentDescription(description) }.isSuccess) return
    }
    showViewerChrome()
    clickContentDescription(description)
}

/**
 * True while the viewer's page is [name]. The page carries the file name as its content
 * description, as the grid cell behind the viewer does, so they are told apart by width:
 * a page is the screen, a cell is a fraction of it. Reads nothing off the chrome, which
 * hides itself, so this says whether the feed moved rather than whether the bar is up.
 */
private fun ComposeTestRule.isViewerPage(name: String): Boolean {
    val screen = onRoot().fetchSemanticsNode().size.width
    return onAllNodes(
        hasContentDescription(name, substring = false, ignoreCase = true) and
            SemanticsMatcher("at least half the screen wide") { it.size.width * 2 >= screen },
    ).fetchSemanticsNodes().isNotEmpty()
}

/**
 * Swipes the feed on until the viewer's page is [name].
 *
 * One swipe is usually it. The drag is kept inside the middle of the screen rather than
 * running edge to edge, where an injected gesture can be dropped, and it is repeated
 * because a slow runner can miss a fling: another swipe on the last page does nothing, so
 * repeating cannot overshoot.
 */
fun ComposeTestRule.pageViewerTo(name: String) {
    repeat(PAGE_SWIPES) {
        if (isViewerPage(name)) return
        onRoot().performTouchInput {
            swipeUp(startY = height * 0.8f, endY = height * 0.2f, durationMillis = 250)
        }
        waitForIdle()
    }
    assertTrue("the feed never paged to $name", isViewerPage(name))
}

private const val PAGE_SWIPES = 4

/**
 * Reads the viewer's chrome, which hides itself three seconds after the last touch and can
 * do so mid-assertion. Each attempt taps the page, which toggles the chrome back up, so
 * [shown] gets several looks at a bar that is actually on screen.
 */
private fun ComposeTestRule.onChrome(what: String, shown: () -> Boolean) {
    repeat(CHROME_ATTEMPTS) {
        waitForIdle()
        if (shown()) return
        onRoot().performTouchInput { click(center) }
    }
    waitForIdle()
    assertTrue("the viewer chrome never showed $what", shown())
}

/** Waits for the viewer's chrome to show all of [texts] at once: its title, its subtitle. */
fun ComposeTestRule.waitForViewerChrome(vararg texts: String) =
    onChrome(texts.joinToString(" and ")) { texts.all { showsText(it) } }

/** One of [names] is the viewer's title; which one a shuffle picked is random. */
fun ComposeTestRule.waitForViewerTitleIn(names: Collection<String>) =
    onChrome("one of $names") { names.any { showsText(it, substring = false) } }

/** Chrome taps before giving up. Every other one leaves the bar up, so half are real looks. */
private const val CHROME_ATTEMPTS = 8

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

/**
 * Inside the open sheet, rather than the explorer behind it. The anchor is the sheet's drag
 * handle: it sits outside the scrolling list, so it identifies the sheet at any scroll
 * position, which a title item scrolled out of composition no longer does.
 */
private val inOpenSheet = hasAnyAncestor(
    hasAnyDescendant(hasContentDescription("Drag handle", substring = false, ignoreCase = true)),
)

/** A node inside the open sheet, by the content description [cd]. */
fun ComposeTestRule.inSheet(cd: String): SemanticsNodeInteraction =
    onNode(hasContentDescription(cd, substring = false, ignoreCase = true) and inOpenSheet)

/**
 * Scrolls the open sheet until [text] is composed. A full-height sheet only composes the
 * rows around the fold, so a section further down does not exist until it does.
 *
 * The list is re-fetched per attempt: the vault's numbers refresh underneath it, and a
 * recomposition between finding the list and scrolling it drops the node mid-gesture.
 */
fun ComposeTestRule.scrollSheetTo(text: String) {
    val target = hasText(text, substring = true, ignoreCase = true)
    repeat(3) {
        waitForIdle()
        if (runCatching { onNode(hasScrollAction() and inOpenSheet).performScrollToNode(target) }.isSuccess) {
            waitForText(text)
            return
        }
    }
    onNode(hasScrollAction() and inOpenSheet).performScrollToNode(target)
    waitForText(text)
}

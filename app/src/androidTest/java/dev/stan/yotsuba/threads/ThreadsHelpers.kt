package dev.stan.yotsuba.threads

import androidx.compose.ui.test.junit4.ComposeTestRule
import dev.stan.yotsuba.clickText
import dev.stan.yotsuba.waitForContentDescription

/** The action each segment puts in the Threads app bar; how a test knows which one is showing. */
private fun segmentAction(segment: String) = if (segment == "Watched") "Bookmark options" else "Clear all"

/**
 * Leaves the Threads tab's other segment and comes back, so [segment]'s list is composed
 * from scratch.
 *
 * A row dismissed by a swipe keeps its dismissed state under its own item key for as long
 * as the list lives, so the row an undo puts back can still be sitting at that anchor, off
 * screen. A list composed again has no such state to restore.
 */
fun ComposeTestRule.recomposeSegment(segment: String) {
    val other = if (segment == "Watched") "Recent" else "Watched"
    clickText(other, substring = false)
    waitForContentDescription(segmentAction(other))
    clickText(segment, substring = false)
    waitForContentDescription(segmentAction(segment))
}

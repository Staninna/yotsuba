package dev.stan.yotsuba.shell

import android.content.Intent
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import dev.stan.yotsuba.MainActivity
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.waitForText
import kotlin.math.abs
import org.junit.Assert.assertTrue

/** An ACTION_VIEW intent for a 4chan URL, pinned to our activity so no browser chooser gets in the way. */
fun viewIntent(url: String): Intent =
    Intent(Intent.ACTION_VIEW, url.toUri()).setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)

fun threadUrl(board: String, threadNo: Long, postNo: Long? = null): String =
    "https://boards.4chan.org/$board/thread/$threadNo" + (postNo?.let { "#p$it" } ?: "")

/** The row carrying [upper] sits above the one carrying [lower]: list order without indexing into `onAllNodes`. */
fun ComposeTestRule.assertAbove(upper: String, lower: String) {
    val top = nodeWithText(upper).fetchSemanticsNode().boundsInRoot.top
    val bottom = nodeWithText(lower).fetchSemanticsNode().boundsInRoot.top
    assertTrue("expected \"$upper\" above \"$lower\"", top < bottom)
}

/**
 * The node matching [matcher] on the same line as the exact text [rowText].
 *
 * A plain Row contributes no semantics node of its own, so a label and the control beside it
 * are siblings of every other row's label and control rather than of each other. Position is
 * the only thing left that says which row a control belongs to.
 */
fun ComposeTestRule.nodeOnLineWith(rowText: String, matcher: SemanticsMatcher): SemanticsNodeInteraction {
    // Bounds are clipped to the scrolling viewport, so a row below the fold has no line.
    val label = onNode(hasTextExactly(rowText), useUnmergedTree = true).performScrollTo()
    val line = label.fetchSemanticsNode().boundsInRoot.center.y
    val candidates = onAllNodes(matcher, useUnmergedTree = true)
    val index = candidates.fetchSemanticsNodes()
        .indexOfFirst { abs(it.boundsInRoot.center.y - line) < it.size.height / 2f }
    require(index >= 0) { "nothing matched on the line of \"$rowText\"" }
    return candidates[index]
}

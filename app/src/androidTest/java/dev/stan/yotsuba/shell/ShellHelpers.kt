package dev.stan.yotsuba.shell

import android.content.Intent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import dev.stan.yotsuba.MainActivity
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.waitForText
import org.junit.Assert.assertTrue

/**
 * A bottom-bar item by its label. The Home tab's title is also "Home" while no board is
 * showing, so the label alone matches two nodes; the click action picks the bar item.
 */
fun ComposeTestRule.tab(label: String): SemanticsNodeInteraction =
    onNode(hasText(label, substring = false) and hasClickAction())

fun ComposeTestRule.tapTab(label: String) {
    waitForText(label, substring = false)
    tab(label).performClick()
}

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

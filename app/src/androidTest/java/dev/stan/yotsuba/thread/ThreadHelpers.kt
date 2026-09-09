package dev.stan.yotsuba.thread

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.PostAnnotation
import dev.stan.yotsuba.domain.model.PostGraph
import dev.stan.yotsuba.domain.model.PostSegment
import dev.stan.yotsuba.domain.model.PostText
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForText

/*
 * Selectors for the thread screen. A post's card carries the "Post actions" accessibility
 * action, which is how a card is told apart from the same post shown again inside a sheet.
 */

/** Nodes inside a bottom sheet or dialog, which Material puts in its own window. */
val inSheet: SemanticsMatcher = hasAnyAncestor(isDialog())

fun hasCustomAction(label: String): SemanticsMatcher = SemanticsMatcher("has custom action '$label'") { node ->
    node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
}

/** A card of the thread list (not a sheet's copy of it) that shows [postText]. */
fun postCard(postText: String): SemanticsMatcher =
    hasCustomAction("Post actions") and hasAnyDescendant(hasText(postText, substring = true, ignoreCase = true))

/** Inside the list's card for the post that shows [postText]. */
fun inPost(postText: String): SemanticsMatcher = hasAnyAncestor(postCard(postText))

/**
 * The tappable runs of a body (quotelinks, links, deadlinks, a still-hidden spoiler): Compose
 * lays each one out as a clickable child of the text. [where] narrows to the list or a sheet.
 */
fun bodyLinks(bodyText: String, where: SemanticsMatcher = !inSheet): SemanticsMatcher =
    hasClickAction() and hasAnyAncestor(hasText(bodyText, substring = true, ignoreCase = true)) and where

/** The one tappable run in the body that reads [bodyText]. Every seeded body has at most one. */
fun ComposeTestRule.bodyLink(bodyText: String, where: SemanticsMatcher = !inSheet): SemanticsNodeInteraction =
    onNode(bodyLinks(bodyText, where), useUnmergedTree = true)

/** Scrolls the thread list until a node showing [text] is composed. */
fun ComposeTestRule.scrollThreadTo(text: String) {
    onNode(hasScrollToNodeAction() and !inSheet)
        .performScrollToNode(hasText(text, substring = true, ignoreCase = true))
}

/** Waits for [text] in the thread list, then scrolls it into view and returns it. */
fun ComposeTestRule.listNode(text: String): SemanticsNodeInteraction {
    waitForText(text)
    scrollThreadTo(text)
    return onNode(hasText(text, substring = true, ignoreCase = true) and !inSheet)
}

/**
 * Holds the card of the post showing [postText], on its top padding: the body text and
 * the header's tappable pieces have gesture handlers of their own that would take the hold.
 */
fun ComposeTestRule.longPressPost(postText: String) {
    listNode(postText)
    onNode(postCard(postText)).performTouchInput { longClick(Offset(centerX, 5f)) }
}

/** Opens the top bar's overflow menu and picks [item] (an exact label). */
fun ComposeTestRule.tapMenuItem(item: String) {
    tapIcon("More options")
    tap(item, substring = false)
}

/** [posts] as a thread, with the backlinks the network mapper would have derived. */
fun threadOf(posts: List<ThreadPost>, board: String = TestSeed.BOARD, threadNo: Long = TestSeed.THREAD_NO) =
    ThreadDetails(board, threadNo, posts, archived = false, closed = false, backlinks = PostGraph.backlinksOf(posts))

/** A reply quoting [target] and then saying [text]. */
fun reply(no: Long, target: Long, text: String): ThreadPost = TestSeed.post(
    TestSeed.BOARD, no,
    PostText(
        listOf(
            PostSegment(">>$target", annotation = PostAnnotation.QuotelinkSameThread(target)),
            PostSegment("\n$text"),
        ),
    ),
)

/** The seeded thread followed by [count] plain replies numbered from [TestSeed.THREAD_NO] + 100. */
fun longThread(count: Int): ThreadDetails = threadOf(
    TestSeed.threadDetails.posts + (1..count).map { TestSeed.post(TestSeed.BOARD, TestSeed.THREAD_NO + 100 + it, fillerText(it)) },
)

fun fillerText(i: Int) = "Filler post number $i."

@file:OptIn(ExperimentalTestApi::class)

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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
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
import dev.stan.yotsuba.waitUntilTrue

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

/** The thread's own list, told apart from a sheet's list by the window it lives in. */
val threadList: SemanticsMatcher = hasScrollToNodeAction() and !inSheet

/**
 * The thread's list, once it is the only one: mid-transition the screen being left behind
 * still has one, and either would answer the matcher.
 */
fun ComposeTestRule.threadListNode(): SemanticsNodeInteraction {
    waitUntilTrue { onAllNodes(threadList).fetchSemanticsNodes().size == 1 }
    return onNode(threadList)
}

/**
 * Scrolls the thread list until a node showing [text] is composed, then lets it settle: a
 * tap that lands while the list is still gliding is consumed by the scroll, not the card.
 */
fun ComposeTestRule.scrollThreadTo(text: String) {
    threadListNode().performScrollToNode(hasText(text, substring = true, ignoreCase = true))
    waitForIdle()
}

/**
 * The node showing [text] in the thread list, scrolled into view. The scroll comes first:
 * a post further down the thread than the list has composed does not exist to wait for.
 */
fun ComposeTestRule.listNode(text: String): SemanticsNodeInteraction {
    scrollThreadTo(text)
    return onNode(hasText(text, substring = true, ignoreCase = true) and !inSheet)
}

/** Scrolls the post showing [bodyText] into view, then taps the one tappable run in its body. */
fun ComposeTestRule.tapBodyLink(bodyText: String) {
    listNode(bodyText)
    bodyLink(bodyText).performClick()
}

/** Text inside an open sheet or dialog, where the same string is usually also on the screen behind. */
fun sheetText(text: String): SemanticsMatcher =
    hasText(text, substring = true, ignoreCase = true) and inSheet

fun ComposeTestRule.sheetNode(text: String): SemanticsNodeInteraction = onNode(sheetText(text))

fun ComposeTestRule.hasSheetText(text: String): Boolean =
    onAllNodes(sheetText(text)).fetchSemanticsNodes().isNotEmpty()

fun ComposeTestRule.waitForSheetText(text: String) =
    waitUntilTrue { onAllNodes(sheetText(text)).fetchSemanticsNodes().isNotEmpty() }

/**
 * Waits until no sheet or dialog window is up. Opening one while another is still sliding
 * away leaves two compose roots mid-layout, which the test framework fetches semantics
 * across; that race shows up as a measure-during-measure crash rather than a bad assertion.
 */
fun ComposeTestRule.waitForNoSheet() =
    waitUntilTrue { onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }

fun ComposeTestRule.waitForSheetTextGone(text: String) =
    waitUntilTrue { onAllNodes(sheetText(text)).fetchSemanticsNodes().isEmpty() }

/**
 * A post card inside a sheet, which is tappable there: tapping refocuses the preview on it.
 * Unmerged, since the tappable wrapper merges the card's text into itself.
 */
fun ComposeTestRule.previewCard(postText: String): SemanticsNodeInteraction =
    onNode(
        hasClickAction() and inSheet and hasAnyDescendant(hasText(postText, substring = true, ignoreCase = true)),
        useUnmergedTree = true,
    )

/**
 * Taps the sheet's card for the post showing [postText], on its top padding: the centre of
 * a card can be a quotelink in its body, which takes the tap and refocuses nothing.
 */
fun ComposeTestRule.tapPreviewCard(postText: String) {
    previewCard(postText).performTouchInput { click(Offset(centerX, 5f)) }
}

/**
 * Opens the action sheet of the post showing [postText] through the card's own "Post actions"
 * accessibility action. A held card is the gesture behind it, and [holdPost] injects that,
 * but the action needs no coordinates inside a card that may have just been scrolled up.
 */
fun ComposeTestRule.longPressPost(postText: String) {
    waitForNoSheet()
    listNode(postText)
    onNode(postCard(postText)).performCustomAccessibilityActionWithLabel("Post actions")
}

/** The real hold on the card of the post showing [postText], on its top padding, clear of its controls. */
fun ComposeTestRule.holdPost(postText: String) {
    waitForNoSheet()
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

package dev.stan.yotsuba.thread

import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.hasText
import androidx.compose.ui.test.assertIsDisplayed
import dev.stan.yotsuba.goBack
import dev.stan.yotsuba.openSeededThread
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import org.junit.Assert.assertFalse
import org.junit.Test

/** Moving around a thread and between it and its catalog neighbours. */
@HiltAndroidTest
class ThreadNavigationFlowTest : FlowTest() {

    private val key = TestSeed.BOARD to TestSeed.THREAD_NO

    private fun swipe(next: Boolean) = composeRule.threadListNode().performTouchInput {
        if (next) swipeLeft() else swipeRight()
    }

    @Test
    fun horizontalSwipe_walksTheCatalogOrder_bothWays() {
        composeRule.openSeededThread()

        swipe(next = true)
        composeRule.waitForText(TestSeed.STICKY_REPLY_TEXT)

        swipe(next = false)
        composeRule.waitForText(TestSeed.REPLY_TEXT)
    }

    @Test
    fun swipingPastTheEnds_saysThereIsNothingThere() {
        composeRule.openSeededThread()
        // The seeded thread is the first the catalog showed.
        swipe(next = false)
        composeRule.waitForText("No previous thread")

        composeRule.goBack()
        composeRule.tap(TestSeed.CLOSED_SUBJECT)
        composeRule.waitForText(TestSeed.CLOSED_OP_TEXT)
        swipe(next = true)
        composeRule.waitForText("No next thread")
    }

    @Test
    fun backAfterASwipe_landsOnTheCatalog() {
        composeRule.openSeededThread()
        swipe(next = true)
        composeRule.waitForText(TestSeed.STICKY_REPLY_TEXT)

        pressBack()
        // The catalog lists every thread, including the ones neither screen showed.
        composeRule.waitForText(TestSeed.CLOSED_SUBJECT)
    }

    @Test
    fun jumpButtons_takeTheListToBothEnds() {
        fakes.threads.threads[key] = longThread(FILLERS)
        composeRule.openSeededThread()
        composeRule.listNode(fillerText(FILLERS))

        composeRule.tapIcon("Jump to top")
        composeRule.waitForTextGone(fillerText(FILLERS))
        composeRule.waitForText(TestSeed.OP_TEXT)

        composeRule.tapIcon("Jump to bottom")
        composeRule.waitForText(fillerText(FILLERS))
    }

    /** Loads the thread, then answers the next fetch with two more posts than it had. */
    private fun openThenRefreshWithTwoNewPosts() {
        fakes.threads.threads[key] = longThread(FILLERS)
        composeRule.openSeededThread()

        fakes.threads.threads[key] = threadOf(
            longThread(FILLERS).posts + listOf(
                TestSeed.post(TestSeed.BOARD, TestSeed.THREAD_NO + 200, FIRST_NEW_TEXT),
                TestSeed.post(TestSeed.BOARD, TestSeed.THREAD_NO + 201, "The second post that arrived"),
            ),
        )
        composeRule.tapIcon("Refresh")
    }

    @Test
    fun aRefreshThatBringsNewPosts_marksThem_untilTheMarkIsTapped() {
        openThenRefreshWithTwoNewPosts()

        composeRule.listNode("2 new posts").assertIsDisplayed()
        composeRule.tap("2 new posts")
        composeRule.waitForTextGone("2 new posts")
    }

    @Test
    fun theFirstNewPostFab_jumpsToTheNewPosts() {
        openThenRefreshWithTwoNewPosts()

        composeRule.tapIcon("Jump to first new post")
        composeRule.waitForText(FIRST_NEW_TEXT)
    }

    @Test
    fun aWatchedThreadOpensPastWhatWasRead_untilTheEarlierRowIsTapped() {
        fakes.threads.threads[key] = longThread(FILLERS)
        fakes.bookmarks.seed(TestSeed.bookmark())
        fakes.history.readMarks[key] = TestSeed.THREAD_NO + 120
        composeRule.openSeededThread()

        composeRule.waitForText("earlier posts")
        composeRule.waitForTextGone(TestSeed.REPLY_TEXT)

        composeRule.tap("earlier posts")
        composeRule.waitForText(TestSeed.REPLY_TEXT)
        composeRule.waitForTextGone("earlier posts")
    }

    @Test
    fun treeView_foldsRepliesDeeperThanTheIndentCap() {
        val chain = (1..6).map { reply(TestSeed.THREAD_NO + it, TestSeed.THREAD_NO + it - 1, "Chain reply $it of six") }
        fakes.threads.threads[key] = threadOf(
            listOf(
                TestSeed.post(TestSeed.BOARD, TestSeed.THREAD_NO, TestSeed.OP_TEXT, isOp = true, subject = TestSeed.THREAD_SUBJECT),
            ) + chain,
        )
        composeRule.openSeededThread()
        composeRule.tapMenuItem("Tree view")

        // Five levels deep is the cap; what hangs below it folds into one row.
        composeRule.listNode("2 more replies").assertIsDisplayed()
        assertFalse(composeRule.hasText("Chain reply 6 of six"))

        composeRule.tap("2 more replies")
        composeRule.listNode("Chain reply 6 of six").assertIsDisplayed()
    }

    private companion object {
        const val FILLERS = 40
        const val FIRST_NEW_TEXT = "The first post that arrived while reading"
    }
}

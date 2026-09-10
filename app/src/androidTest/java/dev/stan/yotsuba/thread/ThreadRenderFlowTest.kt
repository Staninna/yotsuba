package dev.stan.yotsuba.thread

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.goBack
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.openSeededThread
import dev.stan.yotsuba.openThread
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Test

/** Every kind of post body the seed carries renders, plus the OP's badges and backlinks. */
@HiltAndroidTest
class ThreadRenderFlowTest : FlowTest() {

    @Test
    fun seededThread_rendersEveryKindOfBody() {
        composeRule.openSeededThread()
        listOf(
            TestSeed.REPLY_TEXT,
            TestSeed.SPOILER_REPLY_TEXT,
            ">>${TestSeed.THREAD_NO} (OP)",
            TestSeed.LINK_URL,
            ">>>/${TestSeed.BOARD}/${TestSeed.STICKY_THREAD_NO}",
            ">>${TestSeed.DEADLINK_POST_NO} ${TestSeed.DEADLINK_REPLY_TEXT}",
            TestSeed.GREENTEXT_LINE,
        ).forEach { composeRule.listNode(it).assertIsDisplayed() }
        // The OP: its subject under the header, and the backlink to the reply quoting it.
        composeRule.listNode(TestSeed.THREAD_SUBJECT).assertIsDisplayed()
        composeRule.listNode("Quoted by:").assertIsDisplayed()
        composeRule.nodeWithText(">>${TestSeed.THREAD_NO + 3}", substring = false).assertIsDisplayed()
    }

    @Test
    fun textSpoiler_isTappableUntilRevealed() {
        composeRule.openSeededThread()
        composeRule.listNode(TestSeed.GREENTEXT_LINE)
        composeRule.bodyLink(TestSeed.GREENTEXT_LINE).performClick()
        // Once shown the run is plain text; nothing in the body is left to tap.
        composeRule.waitUntilTrue {
            composeRule.onAllNodes(bodyLinks(TestSeed.GREENTEXT_LINE), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        composeRule.listNode(TestSeed.SPOILER_TEXT).assertIsDisplayed()
    }

    @Test
    fun revealAllSpoilers_showsTheTextSpoilerAtOnce() {
        fakes.settings.set { it.copy(revealAllSpoilers = true) }
        composeRule.openSeededThread()
        composeRule.listNode(TestSeed.SPOILER_TEXT).assertIsDisplayed()
        composeRule.onAllNodes(bodyLinks(TestSeed.GREENTEXT_LINE), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun siblingThreads_showStickyAndClosedBadges() {
        composeRule.openThread(TestSeed.BOARD_TITLE, TestSeed.STICKY_SUBJECT, TestSeed.STICKY_OP_TEXT)
        composeRule.nodeWithText("Sticky", substring = false).assertIsDisplayed()
        composeRule.goBack()
        composeRule.tap(TestSeed.CLOSED_SUBJECT)
        composeRule.waitForText(TestSeed.CLOSED_OP_TEXT)
        composeRule.nodeWithText("Closed", substring = false).assertIsDisplayed()
    }

    @Test
    fun posterIdPills_countPosts_andFilterTheThreadOnTap() {
        composeRule.openThread(TestSeed.VIDEO_BOARD_TITLE, TestSeed.VIDEO_SUBJECT, TestSeed.VIDEO_OP_TEXT)
        val opPill = "${TestSeed.VIDEO_OP_POSTER_ID} · 2 posts"
        composeRule.listNode(TestSeed.VIDEO_REPLY_TEXT)
        composeRule.onNode(hasTextExactly("${TestSeed.VIDEO_REPLY_POSTER_ID} · 2 posts") and inPost(TestSeed.VIDEO_REPLY_TEXT))
            .assertIsDisplayed()
        composeRule.listNode(TestSeed.VIDEO_OP_TEXT)
        composeRule.onNode(hasTextExactly(opPill) and inPost(TestSeed.VIDEO_OP_TEXT)).performClick()

        composeRule.waitForText("ID: ${TestSeed.VIDEO_OP_POSTER_ID}")
        composeRule.waitForTextGone(TestSeed.VIDEO_REPLY_TEXT)
        composeRule.listNode(TestSeed.VIDEO_SAME_POSTER_TEXT).assertIsDisplayed()
        composeRule.tapIcon("Clear filter")
        composeRule.waitForTextGone("ID: ${TestSeed.VIDEO_OP_POSTER_ID}")
        composeRule.listNode(TestSeed.VIDEO_REPLY_TEXT).assertIsDisplayed()
    }

    @Test
    fun aFileTheServerDeleted_saysSoInPlaceOfTheThumbnail() {
        fakes.threads.threads[TestSeed.BOARD to TestSeed.THREAD_NO] = threadOf(
            listOf(
                TestSeed.post(TestSeed.BOARD, TestSeed.THREAD_NO, TestSeed.OP_TEXT, isOp = true, subject = TestSeed.THREAD_SUBJECT),
                TestSeed.post(TestSeed.BOARD, TestSeed.THREAD_NO + 1, TestSeed.REPLY_TEXT, media = PostMedia.Deleted("gone.png")),
            ),
        )
        composeRule.openSeededThread()
        composeRule.listNode("File deleted: gone.png").assertIsDisplayed()
    }

    @Test
    fun postNumberTap_copiesItAndSaysSo() {
        composeRule.openSeededThread()
        composeRule.tap("#${TestSeed.THREAD_NO}", substring = false)
        composeRule.waitForText("Post number copied")
    }
}

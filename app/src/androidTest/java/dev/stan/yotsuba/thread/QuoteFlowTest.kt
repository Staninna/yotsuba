package dev.stan.yotsuba.thread

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.ArchiveSource
import dev.stan.yotsuba.domain.model.QuoteTapAction
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.openSeededThread
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Quotelinks, backlinks and the preview sheet they open: both quote-tap settings, the
 * preview stack, the folds, and the ghosts behind a cross-thread quote and a deadlink.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
class QuoteFlowTest : FlowTest() {

    private val opNo = TestSeed.THREAD_NO
    private val quotingNo = TestSeed.THREAD_NO + 3

    /** The ">>1003" backlink under the OP, which the seed's quote reply put there. */
    private fun backlink() = composeRule.onNode(hasTextExactly(">>$quotingNo") and inPost(TestSeed.OP_TEXT))

    @Test
    fun quotelinkTap_opensThePreview_andGoToJumpsAndClosesIt() {
        composeRule.openSeededThread()
        composeRule.tapBodyLink(TestSeed.QUOTE_REPLY_TEXT)

        composeRule.waitForSheetText(TestSeed.OP_TEXT)
        composeRule.sheetNode(">>$opNo").assertIsDisplayed()

        composeRule.tap("Go to")
        composeRule.waitForTextGone("Go to")
        composeRule.listNode(TestSeed.OP_TEXT).assertIsDisplayed()
    }

    @Test
    fun closePreview_closesTheSheet() {
        composeRule.openSeededThread()
        composeRule.tapBodyLink(TestSeed.QUOTE_REPLY_TEXT)
        composeRule.waitForSheetText(TestSeed.OP_TEXT)

        composeRule.tapIcon("Close preview")
        composeRule.waitForTextGone("Go to")
    }

    @Test
    fun backlinkTap_previewsTheQuotingPost_andHoldJumpsInstead() {
        composeRule.openSeededThread()
        composeRule.listNode("Quoted by:")
        backlink().performClick()
        composeRule.waitForSheetText(TestSeed.QUOTE_REPLY_TEXT)
        composeRule.tapIcon("Close preview")
        composeRule.waitForTextGone("Go to")

        // The hold does the other thing: a jump, with no sheet.
        backlink().performCustomAccessibilityActionWithLabel("Hold >>$quotingNo")
        composeRule.waitForIdle()
        assertFalse(composeRule.hasText("Go to"))
    }

    @Test
    fun withJumpSetting_theTapJumps_andTheHoldPreviews() {
        fakes.settings.set { it.copy(quoteTap = QuoteTapAction.JUMP) }
        composeRule.openSeededThread()
        composeRule.tapBodyLink(TestSeed.QUOTE_REPLY_TEXT)
        composeRule.waitForIdle()
        assertFalse(composeRule.hasText("Go to"))

        composeRule.listNode("Quoted by:")
        backlink().performCustomAccessibilityActionWithLabel("Hold >>$quotingNo")
        composeRule.waitForSheetText(TestSeed.QUOTE_REPLY_TEXT)
    }

    @Test
    fun previewStack_showsTheBreadcrumb_andSystemBackPopsOnePostAtATime() {
        composeRule.openSeededThread()
        composeRule.tapBodyLink(TestSeed.QUOTE_REPLY_TEXT)
        composeRule.waitForSheetText(TestSeed.OP_TEXT)

        // The OP's reply is listed under it; tapping it refocuses the sheet a level deeper.
        composeRule.previewCard(TestSeed.QUOTE_REPLY_TEXT).performClick()
        composeRule.waitForText(">>$opNo › >>$quotingNo")
        composeRule.waitForContentDescription("Back to previous post")

        pressBack()
        composeRule.waitForTextGone(">>$opNo › >>$quotingNo")
        composeRule.waitForSheetText(TestSeed.OP_TEXT)
        // Only now does back close the sheet.
        pressBack()
        composeRule.waitForTextGone("Go to")
        composeRule.listNode(TestSeed.OP_TEXT).assertIsDisplayed()
    }

    @Test
    fun previewSheet_foldsParentsAndReplies() {
        // A chain OP -> A -> B -> C, so the sheet on A has a parent above and a nested reply below.
        fakes.threads.threads[TestSeed.BOARD to opNo] = threadOf(
            listOf(
                TestSeed.post(TestSeed.BOARD, opNo, TestSeed.OP_TEXT, isOp = true, subject = TestSeed.THREAD_SUBJECT),
                reply(opNo + 1, opNo, REPLY_A),
                reply(opNo + 2, opNo + 1, REPLY_B),
                reply(opNo + 3, opNo + 2, REPLY_C),
            ),
        )
        composeRule.openSeededThread()
        composeRule.tapBodyLink(REPLY_B) // its quotelink points at A

        // Parents fold shut until asked for.
        composeRule.waitForText("Replying to 1 post")
        assertFalse(composeRule.hasSheetText(TestSeed.OP_TEXT))
        composeRule.tapIcon("Show quoted posts")
        composeRule.waitForSheetText(TestSeed.OP_TEXT)
        composeRule.waitForContentDescription("Hide quoted posts")

        // B is a reply of A carrying one of its own; folding it takes C away.
        composeRule.waitForSheetText(REPLY_C)
        composeRule.tapIcon("Fold replies")
        composeRule.waitForSheetTextGone(REPLY_C)
        composeRule.waitForContentDescription("Unfold replies")
    }

    @Test
    fun crossThreadQuote_opensTheGhost_andOpenThreadNavigatesToIt() {
        composeRule.openSeededThread()
        composeRule.tapBodyLink(TestSeed.CROSS_QUOTE_REPLY_TEXT)

        composeRule.waitForSheetText(TestSeed.STICKY_OP_TEXT)
        composeRule.sheetNode("From /${TestSeed.BOARD}/${TestSeed.STICKY_THREAD_NO} · Live").assertIsDisplayed()

        composeRule.tap("Open thread")
        composeRule.waitForText(TestSeed.STICKY_REPLY_TEXT)
    }

    @Test
    fun deadlink_opensTheSheetSayingThePostIsGone() {
        composeRule.openSeededThread()
        composeRule.tapBodyLink(TestSeed.DEADLINK_REPLY_TEXT)

        composeRule.waitForSheetText("Post not found")
        composeRule.sheetNode(">>${TestSeed.DEADLINK_POST_NO}").assertIsDisplayed()
    }

    @Test
    fun deadlink_findsThePostInTheArchivedCopy() {
        fakes.threads.archived[TestSeed.BOARD to opNo] = threadOf(
            TestSeed.threadDetails.posts + TestSeed.post(TestSeed.BOARD, TestSeed.DEADLINK_POST_NO, PRUNED_TEXT),
        ).copy(archive = ArchiveSource.DESU)
        composeRule.openSeededThread()
        composeRule.tapBodyLink(TestSeed.DEADLINK_REPLY_TEXT)

        composeRule.waitForSheetText(PRUNED_TEXT)
        composeRule.sheetNode("Archived copy (desu)").assertIsDisplayed()
    }

    private companion object {
        const val REPLY_A = "Reply A of the chain"
        const val REPLY_B = "Reply B of the chain"
        const val REPLY_C = "Reply C of the chain"
        const val PRUNED_TEXT = "The archive still has this pruned post"
    }
}

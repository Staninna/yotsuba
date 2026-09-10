package dev.stan.yotsuba.thread

import androidx.compose.ui.test.assertIsDisplayed
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.openSeededThread
import dev.stan.yotsuba.openThread
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** The post action sheet a long-press opens, and every row in it. */
@HiltAndroidTest
class PostActionsFlowTest : FlowTest() {

    /** The seeded OP, marked as the user's own. */
    private val claimOnTheOp = Triple(TestSeed.BOARD, TestSeed.THREAD_NO, TestSeed.THREAD_NO)

    @Test
    fun longPress_opensTheSheet_andCopyTextSaysSo() {
        composeRule.openSeededThread()
        composeRule.longPressPost(TestSeed.QUOTE_REPLY_TEXT)

        composeRule.waitForText("Copy text")
        // The card behind the sheet shows the same number, so this one has to be the sheet's.
        composeRule.sheetNode("#${TestSeed.THREAD_NO + 3}").assertIsDisplayed()
        // A post with no attachment has nothing to copy the URL of.
        assertFalse(composeRule.hasText("Copy image URL"))

        composeRule.tap("Copy text")
        composeRule.waitForText("Post text copied")
    }

    /** The hold itself, on the OP, which never has to be scrolled to. */
    @Test
    fun aHeldCard_opensItsSheet() {
        composeRule.openSeededThread()
        composeRule.holdPost(TestSeed.OP_TEXT)

        composeRule.waitForText("Copy text")
        composeRule.sheetNode("#${TestSeed.THREAD_NO}").assertIsDisplayed()
    }

    @Test
    fun copyImageUrl_onlyOnTheImagePost() {
        composeRule.openSeededThread()
        composeRule.longPressPost(TestSeed.REPLY_TEXT)

        composeRule.tap("Copy image URL")
        composeRule.waitForText("Image URL copied")
    }

    @Test
    fun markAsMine_raisesTheRepliesToYouChip_whichRoutesToTheReply() {
        composeRule.openSeededThread()
        // The OP is quoted by the seeded quote reply, so claiming it is a reply to the user.
        composeRule.longPressPost(TestSeed.OP_TEXT)
        composeRule.tap("Mark as mine")
        composeRule.waitUntilTrue { fakes.claimed.state.value == setOf(claimOnTheOp) }
        composeRule.waitForText("1 reply to you")

        // The chip routes to that reply like a quotelink, once the sheet is out of the way.
        composeRule.waitForNoSheet()
        composeRule.tap("1 reply to you")
        composeRule.waitForSheetText(TestSeed.QUOTE_REPLY_TEXT)
        composeRule.tapIcon("Close preview")
        composeRule.waitForTextGone("Go to")
    }

    @Test
    fun notMine_takesTheChipAwayAgain() {
        fakes.claimed.state.value = setOf(claimOnTheOp)
        composeRule.openSeededThread()
        composeRule.waitForText("1 reply to you")

        composeRule.longPressPost(TestSeed.OP_TEXT)
        composeRule.tap("Not mine")
        composeRule.waitUntilTrue { fakes.claimed.state.value.isEmpty() }
        composeRule.waitForTextGone("1 reply to you")
    }

    @Test
    fun filterById_hidesTheOtherPoster_andClearFilterRestoresIt() {
        composeRule.openThread(TestSeed.VIDEO_BOARD_TITLE, TestSeed.VIDEO_SUBJECT, TestSeed.VIDEO_OP_TEXT)
        composeRule.longPressPost(TestSeed.VIDEO_SAME_POSTER_TEXT)
        composeRule.tap("Filter by ID")

        composeRule.waitForText("ID: ${TestSeed.VIDEO_OP_POSTER_ID}")
        composeRule.waitForTextGone(TestSeed.VIDEO_REPLY_TEXT)
        composeRule.listNode(TestSeed.VIDEO_SAME_POSTER_TEXT).assertIsDisplayed()

        composeRule.tapIcon("Clear filter")
        composeRule.waitForTextGone("ID: ${TestSeed.VIDEO_OP_POSTER_ID}")
        composeRule.listNode(TestSeed.VIDEO_REPLY_TEXT).assertIsDisplayed()
    }

    /** The board with poster IDs is the only one the row shows on. */
    @Test
    fun filterById_isAbsentOnABoardWithoutPosterIds() {
        composeRule.openSeededThread()
        composeRule.longPressPost(TestSeed.REPLY_TEXT)
        composeRule.waitForText("Copy text")
        assertFalse(composeRule.hasText("Filter by ID"))
    }

    @Test
    fun translate_appearsOnlyWithTheSettingOn() {
        composeRule.openSeededThread()
        composeRule.longPressPost(TestSeed.REPLY_TEXT)
        composeRule.waitForText("Copy text")
        assertFalse(composeRule.hasText("Translate"))
        composeRule.tap("Copy text") // closes the sheet

        fakes.settings.set { it.copy(translatePosts = true) }
        composeRule.longPressPost(TestSeed.REPLY_TEXT)
        composeRule.tap("Translate")

        // The block under the post says which state it is in: a result, or a failure on a
        // device with no model. Let it settle before hiding it, since a translation that
        // lands after the tap puts the block back.
        composeRule.waitForText("Translation")
        composeRule.waitForTextGone("Translating…")
        composeRule.waitForTextGone("Downloading the language model…")

        composeRule.tap("Hide")
        composeRule.waitForTextGone("Translation")
    }
}

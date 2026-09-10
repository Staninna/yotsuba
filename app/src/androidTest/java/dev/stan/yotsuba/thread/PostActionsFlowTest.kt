package dev.stan.yotsuba.thread

import androidx.compose.ui.test.assertIsDisplayed
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.nodeWithText
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

    @Test
    fun longPress_opensTheSheet_andCopyTextSaysSo() {
        composeRule.openSeededThread()
        composeRule.longPressPost(TestSeed.QUOTE_REPLY_TEXT)

        composeRule.waitForText("Copy text")
        composeRule.nodeWithText("#${TestSeed.THREAD_NO + 3}", substring = false).assertIsDisplayed()
        // A post with no attachment has nothing to copy the URL of.
        assertFalse(composeRule.hasText("Copy image URL"))

        composeRule.tap("Copy text")
        composeRule.waitForText("Post text copied")
    }

    @Test
    fun copyImageUrl_onlyOnTheImagePost() {
        composeRule.openSeededThread()
        composeRule.longPressPost(TestSeed.REPLY_TEXT)

        composeRule.tap("Copy image URL")
        composeRule.waitForText("Image URL copied")
    }

    @Test
    fun markAsMine_thenNotMine_movesTheRepliesToYouChip() {
        composeRule.openSeededThread()
        // The OP is quoted by the seeded quote reply, so claiming it is a reply to the user.
        composeRule.longPressPost(TestSeed.OP_TEXT)
        composeRule.tap("Mark as mine")
        composeRule.waitUntilTrue {
            fakes.claimed.state.value == setOf(Triple(TestSeed.BOARD, TestSeed.THREAD_NO, TestSeed.THREAD_NO))
        }
        composeRule.waitForText("1 reply to you")

        composeRule.longPressPost(TestSeed.OP_TEXT)
        composeRule.tap("Not mine")
        composeRule.waitUntilTrue { fakes.claimed.state.value.isEmpty() }
        composeRule.waitForTextGone("1 reply to you")
    }

    @Test
    fun filterById_hidesTheOtherPoster_andClearFilterRestoresIt() {
        composeRule.openThread(TestSeed.VIDEO_BOARD_TITLE, TestSeed.VIDEO_SUBJECT, TestSeed.VIDEO_OP_TEXT)
        composeRule.longPressPost(TestSeed.VIDEO_REPLY_TEXT)
        composeRule.tap("Filter by ID")

        composeRule.waitForText("ID: ${TestSeed.VIDEO_REPLY_POSTER_ID}")
        composeRule.waitForTextGone(TestSeed.VIDEO_SAME_POSTER_TEXT)
        composeRule.listNode(TestSeed.SOUND_REPLY_TEXT).assertIsDisplayed()

        composeRule.tapIcon("Clear filter")
        composeRule.waitForTextGone("ID: ${TestSeed.VIDEO_REPLY_POSTER_ID}")
        composeRule.listNode(TestSeed.VIDEO_SAME_POSTER_TEXT).assertIsDisplayed()
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

        // The block under the post says which state it is in; on an emulator with no model
        // that is a failure, and either way "Hide" takes it away again.
        composeRule.waitForText("Translation")
        composeRule.tap("Hide")
        composeRule.waitForTextGone("Translation")
        composeRule.listNode(TestSeed.REPLY_TEXT).assertIsDisplayed()
    }
}

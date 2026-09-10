package dev.stan.yotsuba.thread

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.height
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.backToTabs
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.BoardProfile
import dev.stan.yotsuba.domain.model.FontSize
import dev.stan.yotsuba.domain.model.LineSpacing
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.TimestampMode
import dev.stan.yotsuba.domain.model.TimestampZone
import dev.stan.yotsuba.goBack
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.openSeededThread
import dev.stan.yotsuba.openThread
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        // The OP: its subject under the header (the top bar shows the same string), and the
        // backlink to the reply quoting it.
        composeRule.listNode(TestSeed.OP_TEXT)
        composeRule.onNode(
            hasText(TestSeed.THREAD_SUBJECT, substring = false) and inPost(TestSeed.OP_TEXT),
            useUnmergedTree = true,
        ).assertIsDisplayed()
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

    /**
     * A board profile's text size and line spacing reach nothing a selector can read: the
     * body is the same string either way. What they do change is how much room it takes, so
     * the same body is drawn on both boards and the heights are compared.
     */
    @Test
    fun aBoardProfile_growsThatBoardsPosts_andLeavesTheOtherBoardAlone() {
        fakes.settings.set {
            it.copy(
                boardProfiles = mapOf(
                    TestSeed.VIDEO_BOARD to BoardProfile(fontSize = FontSize.EXTRA_LARGE, lineSpacing = LineSpacing.RELAXED),
                ),
            )
        }
        fakes.threads.threads[TestSeed.BOARD to TestSeed.THREAD_NO] = threadOf(
            listOf(TestSeed.post(TestSeed.BOARD, TestSeed.THREAD_NO, SHARED_BODY, isOp = true, subject = TestSeed.THREAD_SUBJECT)),
        )
        fakes.threads.threads[TestSeed.VIDEO_BOARD to TestSeed.VIDEO_THREAD_NO] = threadOf(
            listOf(TestSeed.post(TestSeed.VIDEO_BOARD, TestSeed.VIDEO_THREAD_NO, SHARED_BODY, isOp = true, subject = TestSeed.VIDEO_SUBJECT)),
            TestSeed.VIDEO_BOARD,
            TestSeed.VIDEO_THREAD_NO,
        )

        composeRule.openThread(TestSeed.VIDEO_BOARD_TITLE, TestSeed.VIDEO_SUBJECT, SHARED_BODY)
        val withProfile = bodyHeight()

        // Back out to the board list by hand: the bottom bar's Boards tab and the Boards
        // screen's own title are the same word, and from here both are on screen.
        composeRule.goBack()
        composeRule.goBack()
        composeRule.tap(TestSeed.BOARD_TITLE)
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        composeRule.tap(TestSeed.THREAD_SUBJECT)
        val plain = bodyHeight()
        assertTrue("$plain on /${TestSeed.BOARD}/ should be shorter than $withProfile on /${TestSeed.VIDEO_BOARD}/", plain < withProfile)
    }

    /**
     * The three post-time modes on one card. The zone is the board's so the clock part is
     * fixed: the seeded OP is 22:13 UTC, which is 5:13 PM in New York, and the seed is old
     * enough that the relative part is always a count of years.
     */
    @Test
    fun postTime_readsAsRelative_thenAbsolute_thenBoth() {
        fakes.settings.set { it.copy(timestampZone = TimestampZone.BOARD) }
        composeRule.openSeededThread()
        composeRule.waitUntilTrue { opStampShows("ago") }
        assertFalse(opStampShows(BOARD_CLOCK))

        fakes.settings.set { it.copy(timestampMode = TimestampMode.ABSOLUTE) }
        composeRule.waitUntilTrue { opStampShows(BOARD_CLOCK) }
        assertFalse(opStampShows("ago"))

        fakes.settings.set { it.copy(timestampMode = TimestampMode.BOTH) }
        composeRule.waitUntilTrue { opStampShows("ago") && opStampShows(BOARD_CLOCK) }
    }

    @Test
    fun postNumberTap_copiesItAndSaysSo() {
        composeRule.openSeededThread()
        composeRule.tap("#${TestSeed.THREAD_NO}", substring = false)
        composeRule.waitForText("Post number copied")
    }

    /** The body Text on screen, once the thread being left behind has stopped showing its own. */
    private fun bodyHeight(): Dp {
        val body = hasText(SHARED_BODY, substring = false) and !inSheet
        composeRule.waitUntilTrue { composeRule.onAllNodes(body, useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
        return composeRule.onNode(body, useUnmergedTree = true).getBoundsInRoot().height
    }

    /** The stamp is a plain Text in the OP's card, so it only exists in the unmerged tree. */
    private fun opStampShows(text: String): Boolean =
        composeRule.onAllNodes(hasText(text, substring = true) and inPost(TestSeed.OP_TEXT), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private companion object {
        /** The seeded OP's time of day in the board's zone, in the short form that locale uses. */
        const val BOARD_CLOCK = "5:13"

        /** One short line, so a taller line height is the whole difference between the boards. */
        const val SHARED_BODY = "The same body on both boards"
    }
}

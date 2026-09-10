package dev.stan.yotsuba.thread

import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.hasContentDescription
import dev.stan.yotsuba.nodeWithContentDescription
import dev.stan.yotsuba.openSeededThread
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a tap and a hold on a post's attachment do: reveal, expand, open the viewer, save. */
@HiltAndroidTest
class ThumbnailFlowTest : FlowTest() {

    /** The seeded reply's thumbnail. Its description is the file name and its dimensions. */
    private fun thumbnail(filename: String = TestSeed.MEDIA_FILENAME) =
        composeRule.nodeWithContentDescription(filename, substring = true)

    @Test
    fun thumbnailTap_opensTheViewer() {
        composeRule.openSeededThread()
        composeRule.tapIcon(TestSeed.MEDIA_FILENAME, substring = true)
        composeRule.waitForContentDescription("Close viewer")
    }

    @Test
    fun spoileredThumbnail_firstTapReveals_secondTapOpensTheViewer() {
        composeRule.openSeededThread()
        composeRule.listNode(TestSeed.SPOILER_REPLY_TEXT)

        // The reveal tap must not also open the viewer.
        thumbnail(TestSeed.SPOILER_FILENAME).performClick()
        composeRule.waitForIdle()
        assertFalse(composeRule.hasContentDescription("Close viewer"))

        thumbnail(TestSeed.SPOILER_FILENAME).performClick()
        composeRule.waitForContentDescription("Close viewer")
    }

    @Test
    fun withRevealAllSpoilers_theFirstTapOpensTheViewer() {
        fakes.settings.set { it.copy(revealAllSpoilers = true) }
        composeRule.openSeededThread()
        composeRule.listNode(TestSeed.SPOILER_REPLY_TEXT)

        thumbnail(TestSeed.SPOILER_FILENAME).performClick()
        composeRule.waitForContentDescription("Close viewer")
    }

    @Test
    fun inlineExpansion_expandsInPlace_theFileLineCollapsesIt_andTheImageOpensTheViewer() {
        fakes.settings.set { it.copy(inlineImageExpansion = true) }
        composeRule.openSeededThread()
        composeRule.listNode(TestSeed.REPLY_TEXT)

        thumbnail().performClick()
        composeRule.waitForText("tap to collapse")
        composeRule.tap("tap to collapse")
        composeRule.waitForTextGone("tap to collapse")

        thumbnail().performClick()
        composeRule.waitForText("tap to collapse")
        // The full image in the card goes to the viewer instead of collapsing.
        thumbnail().performClick()
        composeRule.waitForContentDescription("Close viewer")
    }

    @Test
    fun holdToSave_queuesTheFile_andBadgesTheThumbnail() {
        composeRule.openSeededThread()
        composeRule.listNode(TestSeed.REPLY_TEXT)

        thumbnail().performTouchInput { longClick() }
        composeRule.waitForContentDescription("Saved")

        val (item, context) = fakes.vault.saves.single()
        assertEquals(TestSeed.mediaItem.fullUrl, item.fullUrl)
        assertEquals(TestSeed.BOARD to TestSeed.THREAD_NO, context.board to context.threadNo)
    }

    @Test
    fun withHoldToSaveOff_theHoldSavesNothing() {
        fakes.settings.set { it.copy(holdToSave = false) }
        composeRule.openSeededThread()
        composeRule.listNode(TestSeed.REPLY_TEXT)

        thumbnail().performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertTrue(fakes.vault.saves.isEmpty())
        assertFalse(composeRule.hasContentDescription("Saved"))
    }

    @Test
    fun aQueuedSave_andAFailedOne_saySoOnTheThumbnail() {
        composeRule.openSeededThread()
        composeRule.listNode(TestSeed.REPLY_TEXT)

        fakes.saveQueue.holdNext = true
        thumbnail().performTouchInput { longClick() }
        composeRule.waitForContentDescription("Queued for download")
        assertTrue(fakes.vault.saves.isEmpty())

        // Letting the download finish flips the same badge to saved.
        onActivity { fakes.saveQueue.completeHeld(TestSeed.mediaItem.fullUrl) }
        composeRule.waitForContentDescription("Saved")
    }

    @Test
    fun aFailedSave_saysSoOnTheThumbnail() {
        composeRule.openSeededThread()
        composeRule.listNode(TestSeed.SPOILER_REPLY_TEXT)

        fakes.saveQueue.failNextWith = VaultError.Io(null)
        thumbnail(TestSeed.SPOILER_FILENAME).performTouchInput { longClick() }
        composeRule.waitForContentDescription("Couldn't save")
        assertTrue(fakes.vault.saves.isEmpty())
    }
}

package dev.stan.yotsuba.thread

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.openSeededThread
import dev.stan.yotsuba.openThread
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/** The thread gallery sheet: the file count, the filter chips, a tile tap and save all. */
@HiltAndroidTest
class GalleryFlowTest : FlowTest() {

    private fun openGallery() {
        composeRule.openSeededThread()
        composeRule.tapMenuItem("Gallery")
    }

    /** A gallery tile, whose description is the file's name alone. */
    private fun tile(displayName: String) =
        composeRule.onNode(hasContentDescription(displayName) and inSheet)

    @Test
    fun gallery_listsEverySeededFile_andATileTapOpensTheViewer() {
        openGallery()
        composeRule.waitForText("2 files")

        tile(TestSeed.mediaItem.displayName).performClick()
        composeRule.waitForContentDescription("Close viewer")
    }

    @Test
    fun chips_narrowTheGallery_andSaveAllTakesOnlyWhatIsShown() {
        val clip = TestSeed.mediaItem(TestSeed.THREAD_NO + 2, "gallery_clip", ext = ".webm")
        fakes.threads.threads[TestSeed.BOARD to TestSeed.THREAD_NO] = threadOf(
            listOf(
                TestSeed.post(TestSeed.BOARD, TestSeed.THREAD_NO, TestSeed.OP_TEXT, isOp = true, subject = TestSeed.THREAD_SUBJECT),
                TestSeed.post(TestSeed.BOARD, TestSeed.THREAD_NO + 1, TestSeed.REPLY_TEXT, media = PostMedia.Present(TestSeed.mediaItem)),
                TestSeed.post(TestSeed.BOARD, TestSeed.THREAD_NO + 2, TestSeed.VIDEO_REPLY_TEXT, media = PostMedia.Present(clip)),
            ),
        )
        openGallery()
        composeRule.waitForText("2 files")

        composeRule.tap("Videos", substring = false)
        composeRule.waitForText("1 file", substring = false)
        composeRule.tap("Save all", substring = false)

        composeRule.waitUntilTrue { fakes.vault.saves.isNotEmpty() }
        assertEquals(listOf(clip.fullUrl), fakes.vault.saves.map { (item, _) -> item.fullUrl })
    }

    @Test
    fun soundChips_andTheirHint_onABoardThatServesAudio() {
        composeRule.openThread(TestSeed.VIDEO_BOARD_TITLE, TestSeed.VIDEO_SUBJECT, TestSeed.VIDEO_OP_TEXT)
        composeRule.tapMenuItem("Gallery")
        composeRule.waitForText("2 files")
        composeRule.nodeWithText("On this board every video may have sound.").assertIsDisplayed()

        // Every video on the board may carry audio, so nothing is silent.
        composeRule.tap("With sound", substring = false)
        composeRule.waitForText("2 files")
        composeRule.tap("Silent", substring = false)
        composeRule.waitForText("Nothing matches this filter")
        composeRule.nodeWithText("Save all", substring = false).assertIsNotEnabled()

        composeRule.tap("All", substring = false)
        composeRule.waitForText("2 files")
    }

    @Test
    fun aRepostedFile_foldsIntoOneTileWithACopyCount() {
        val repost = TestSeed.mediaItem(TestSeed.THREAD_NO + 2, "repost_image", md5 = TestSeed.mediaItem.md5)
        fakes.threads.threads[TestSeed.BOARD to TestSeed.THREAD_NO] = threadOf(
            listOf(
                TestSeed.post(TestSeed.BOARD, TestSeed.THREAD_NO, TestSeed.OP_TEXT, isOp = true, subject = TestSeed.THREAD_SUBJECT),
                TestSeed.post(TestSeed.BOARD, TestSeed.THREAD_NO + 1, TestSeed.REPLY_TEXT, media = PostMedia.Present(TestSeed.mediaItem)),
                TestSeed.post(TestSeed.BOARD, TestSeed.THREAD_NO + 2, TestSeed.SPOILER_REPLY_TEXT, media = PostMedia.Present(repost)),
            ),
        )
        openGallery()

        composeRule.waitForText("1 file", substring = false)
        composeRule.waitForText("x2")
    }

    @Test
    fun aThreadWithNoFiles_saysSo() {
        composeRule.openThread(TestSeed.BOARD_TITLE, TestSeed.CLOSED_SUBJECT, TestSeed.CLOSED_OP_TEXT)
        composeRule.tapMenuItem("Gallery")

        composeRule.waitForText("No files in this thread")
        composeRule.nodeWithText("Attachments posted here will show up in the gallery.").assertIsDisplayed()
        composeRule.nodeWithText("Save all", substring = false).assertIsNotEnabled()
    }
}

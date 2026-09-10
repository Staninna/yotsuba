package dev.stan.yotsuba.media

import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.openSeededThread
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Test

@HiltAndroidTest
class ViewerStatesFlowTest : FlowTest() {

    /**
     * Opens the thread, lets [change] rewrite what the next fetch answers, then opens the
     * viewer: the viewer loads the thread itself, so it sees the changed answer.
     */
    private fun openViewerAfter(change: () -> Unit) {
        composeRule.openSeededThread()
        change()
        composeRule.tapIcon(TestSeed.MEDIA_FILENAME, substring = true)
    }

    @Test
    fun threadWithoutMedia_saysSo_andCloses() {
        openViewerAfter {
            fakes.threads.threads[TestSeed.BOARD to TestSeed.THREAD_NO] =
                TestSeed.threadDetails.copy(posts = TestSeed.threadDetails.posts.filter { it.media == null })
        }
        composeRule.waitForText("This thread has no media")
        composeRule.tapIcon(CLOSE_VIEWER)
        composeRule.waitForText(TestSeed.OP_TEXT)
    }

    @Test
    fun failedLoad_showsTheError_andRetryRecovers() {
        openViewerAfter { fakes.threads.failWith = NetworkError.Offline }
        composeRule.waitForText("You're offline")
        fakes.threads.failWith = null
        composeRule.tap("Retry", substring = false)
        composeRule.waitForText("1 / 2 · 12 KB · 800×600", substring = false)
    }

    @Test
    fun holdToSave_longPressOnThePage_savesToTheVault() {
        composeRule.openSeededImage()
        composeRule.viewerImage().performTouchInput { longClick() }
        composeRule.waitUntilTrue { fakes.vault.saves.isNotEmpty() }
        assertEquals(TestSeed.mediaItem.fullUrl, fakes.vault.saves.single().first.fullUrl)
        composeRule.showChrome()
        composeRule.waitForContentDescription("Saved", substring = false)
    }

    @Test
    fun galleryTile_opensTheViewerOnThatPost() {
        composeRule.openSeededThread()
        composeRule.tapIcon("More options")
        composeRule.tap("Gallery", substring = false)
        composeRule.waitForText("2 files")
        composeRule.tapIcon(TestSeed.spoilerMediaItem.displayName)
        composeRule.waitForText("2 / 2 · 7 KB · 640×480", substring = false)
    }
}

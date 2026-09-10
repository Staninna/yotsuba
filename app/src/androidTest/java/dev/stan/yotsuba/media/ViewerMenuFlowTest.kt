package dev.stan.yotsuba.media

import androidx.compose.ui.test.hasStateDescription
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.clickText
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@HiltAndroidTest
class ViewerMenuFlowTest : FlowTest() {

    private val looping = "Looping. Tap to auto-advance"
    private val autoAdvancing = "Auto-advance on. Tap to loop"

    @Test
    fun imageMenu_listsImageActions_andNoVideoOnes() {
        composeRule.openSeededImage()
        composeRule.tapChrome("More")
        composeRule.waitForText("Search image")
        assertTrue(composeRule.hasText("Copy text"))
        assertTrue(composeRule.hasText("Picture-in-picture"))
        assertTrue(composeRule.hasText(looping))
        assertFalse(composeRule.hasText("Search a frame"))
        assertFalse(composeRule.hasText("Export as animated WebP"))
        assertFalse(composeRule.hasText("Loop a section"))
    }

    @Test
    fun videoMenu_listsVideoActions_andNoImageOnes() {
        composeRule.openSeededVideo()
        composeRule.tapChrome("More")
        composeRule.waitForText("Search a frame")
        assertTrue(composeRule.hasText("Export as animated WebP"))
        assertTrue(composeRule.hasText("Loop a section"))
        assertFalse(composeRule.hasText("Search image"))
        assertFalse(composeRule.hasText("Copy text"))
    }

    /**
     * The handles themselves need a duration to sit on, and the seeded URL never resolves,
     * so the toggle and its label are all this emulator can reach.
     */
    @Test
    fun loopSection_resetsOnTheNextPage_andItsLabelFlips() {
        composeRule.openSeededVideo()
        composeRule.openViewerMenu("Loop a section")
        composeRule.nextPage()
        composeRule.waitForText(TestSeed.soundItem.displayName, substring = false)

        // The new page loops whole again, whatever the page before it was set to.
        composeRule.openViewerMenu("Loop a section")
        composeRule.tapChrome("More")
        composeRule.waitForText("Loop the whole video")
        composeRule.clickText("Loop the whole video", substring = false)
        composeRule.tapChrome("More")
        composeRule.waitForText("Loop a section")
    }

    @Test
    fun autoAdvance_menuLabelFlipsOnEachTap() {
        composeRule.openSeededImage()
        composeRule.openViewerMenu(looping)
        composeRule.waitForTextGone(looping)
        composeRule.openViewerMenu(autoAdvancing)
        composeRule.waitForTextGone(autoAdvancing)
        composeRule.tapChrome("More")
        composeRule.waitForText(looping)
    }

    @Test
    fun pictureInPicture_isOfferedAndTakesTheTap() {
        composeRule.openSeededImage()
        composeRule.tapChrome("More")
        composeRule.waitForText("Picture-in-picture")
        // The tap enters picture-in-picture, which takes the compose tree with it, so it is
        // the last thing this test does.
        composeRule.clickText("Picture-in-picture", substring = false)
    }

    @Test
    fun share_preparesTheFile_thenReportsTheFailedFetch() {
        composeRule.openSeededImage()
        composeRule.tapChrome("Share")
        composeRule.waitUntilTrue {
            composeRule.onAllNodes(hasStateDescription("Preparing to share")).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.hasText("Couldn't share")
        }
        composeRule.waitForText("Couldn't share")
    }

    @Test
    fun copyText_onAnUnsavedImage_reportsTheFailedFetch() {
        composeRule.openSeededImage()
        composeRule.openViewerMenu("Copy text")
        // example.invalid fails to resolve before the "Fetching the file…" dialog lasts a
        // frame, so the snackbar is the only assertable end of this route.
        composeRule.waitForText("Couldn't share")
    }

    @Test
    fun copyText_onSavedImage_opensTheSheet_findsNoText() {
        fakes.vault.seedLocalCopy(TestSeed.mediaItem)
        composeRule.openSeededImage()
        composeRule.openViewerMenu("Copy text")
        composeRule.waitForText("Text in this image")
        composeRule.waitForText("No text was recognised in this image")
    }
}

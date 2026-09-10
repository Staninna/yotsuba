package dev.stan.yotsuba.media

import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.SeekStep
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForText
import org.junit.Assert.assertFalse
import org.junit.Test

@HiltAndroidTest
class VideoViewerFlowTest : FlowTest() {

    private val playbackFailed = "This video could not be played"

    /** The vault copy is 64 bytes of nothing, so every decode of it fails fast. */
    private fun seedSavedVideo() {
        fakes.vault.seedLocalCopy(
            TestSeed.videoItem,
            board = TestSeed.VIDEO_BOARD,
            threadNo = TestSeed.VIDEO_THREAD_NO,
        )
    }

    /** Double-taps the outer 10 % of one side; the middle would zoom instead of seeking. */
    private fun doubleTapEdge(fraction: Float) {
        composeRule.onRoot().performTouchInput {
            doubleClick(percentOffset(fraction, 0.5f))
        }
    }

    @Test
    fun video_failsOnTheFakeUrl_andRetryTriesAgain() {
        composeRule.openSeededVideo()
        composeRule.waitForText(playbackFailed)
        composeRule.tap("Retry", substring = false)
        composeRule.waitForText(playbackFailed)
    }

    @Test
    fun soundPost_carriesTheSoundBadge() {
        composeRule.openSeededVideo()
        composeRule.waitForText(TestSeed.videoItem.displayName, substring = false)
        composeRule.nextPage()
        composeRule.waitForText(TestSeed.soundItem.displayName, substring = false)
        composeRule.waitForText("sound", substring = false)
    }

    @Test
    fun transportBar_playsAndFlipsTheMuteButton() {
        composeRule.openSeededVideo()
        // /v/ declares webm audio, so the viewer opens unmuted.
        composeRule.tapChrome("Mute")
        composeRule.waitForContentDescription("Unmute")
        composeRule.tapChrome("Unmute")
        composeRule.waitForContentDescription("Mute")
        composeRule.waitForContentDescription("Play")
    }

    @Test
    fun edgeDoubleTap_showsTheConfiguredSkip() {
        fakes.settings.set { it.copy(seekStep = SeekStep.FIVE) }
        composeRule.openSeededVideo()
        composeRule.waitForText(playbackFailed)
        doubleTapEdge(0.9f)
        composeRule.waitForText("+5 s", substring = false)
        doubleTapEdge(0.1f)
        composeRule.waitForText("−5 s", substring = false)
    }

    @Test
    fun edgeDoubleTap_doesNothing_whenSkippingIsOff() {
        fakes.settings.set { it.copy(doubleTapSeekEnabled = false) }
        composeRule.openSeededVideo()
        composeRule.waitForText(playbackFailed)
        doubleTapEdge(0.9f)
        composeRule.waitForIdle()
        assertFalse(composeRule.hasText("+10 s", substring = false))
    }

    @Test
    fun searchAFrame_reportsAVideoItCannotRead() {
        seedSavedVideo()
        composeRule.openSeededVideo()
        composeRule.openViewerMenu("Search a frame")
        composeRule.waitForText("Couldn't read a frame from this video")
    }

    @Test
    fun exportAsAnimatedWebp_reportsTheFailure() {
        seedSavedVideo()
        composeRule.openSeededVideo()
        composeRule.openViewerMenu("Export as animated WebP")
        composeRule.waitForText("Couldn't export this video")
    }
}

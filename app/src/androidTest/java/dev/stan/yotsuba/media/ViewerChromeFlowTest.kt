package dev.stan.yotsuba.media

import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForContentDescription
import dev.stan.yotsuba.waitForContentDescriptionGone
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import org.junit.Test

@HiltAndroidTest
class ViewerChromeFlowTest : FlowTest() {

    private val panelTitle = ">>${TestSeed.mediaItem.postNo}"
    private val repliesButton = "Replies (0)"

    @Test
    fun open_showsTitle_sizeAndDimensions() {
        composeRule.openSeededImage()
        composeRule.waitForText(TestSeed.mediaItem.displayName, substring = false)
        composeRule.waitForText("1 / 2 · 12 KB · 800×600", substring = false)
    }

    @Test
    fun singleTap_hidesChrome_andShowsItAgain() {
        composeRule.openSeededImage()
        composeRule.tapPage()
        composeRule.waitForContentDescriptionGone(CLOSE_VIEWER)
        composeRule.tapPage()
        composeRule.waitForContentDescription(CLOSE_VIEWER)
    }

    @Test
    fun verticalSwipe_movesToSpoilerImage() {
        composeRule.openSeededImage()
        composeRule.nextPage()
        composeRule.waitForText("2 / 2 · 7 KB · 640×480", substring = false)
        composeRule.waitForText(TestSeed.spoilerMediaItem.displayName, substring = false)
    }

    @Test
    fun closeButton_returnsToThread() {
        composeRule.openSeededImage()
        composeRule.tapChrome(CLOSE_VIEWER)
        composeRule.waitForText(TestSeed.OP_TEXT)
    }

    @Test
    fun repliesButton_opensPanel_backClosesIt() {
        composeRule.openSeededImage()
        composeRule.tapChrome(repliesButton)
        composeRule.waitForText(panelTitle)
        composeRule.waitForText(TestSeed.REPLY_TEXT)
        composeRule.tapIcon("Back")
        composeRule.waitForTextGone(panelTitle)
        composeRule.showChrome()
    }

    @Test
    fun horizontalSwipes_openPanel_closeIt_thenLeaveViewer() {
        composeRule.openSeededImage()
        composeRule.viewerImage().performTouchInput { swipeLeft() }
        composeRule.waitForText(panelTitle)
        composeRule.nodeWithText(TestSeed.REPLY_TEXT).performTouchInput { swipeRight() }
        composeRule.waitForTextGone(panelTitle)
        composeRule.viewerImage().performTouchInput { swipeRight() }
        composeRule.waitForText(TestSeed.OP_TEXT)
    }

    @Test
    fun systemBack_closesPanelFirst_thenViewer() {
        composeRule.openSeededImage()
        composeRule.tapChrome(repliesButton)
        composeRule.waitForText(panelTitle)
        pressBack()
        composeRule.waitForTextGone(panelTitle)
        composeRule.showChrome()
        pressBack()
        composeRule.waitForText(TestSeed.OP_TEXT)
    }
}

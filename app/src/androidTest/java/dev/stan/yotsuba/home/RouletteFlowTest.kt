package dev.stan.yotsuba.home

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.nodeWithContentDescription
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Test

@HiltAndroidTest
class RouletteFlowTest : FlowTest() {

    override fun seed() {
        fakes.settings.set { it.copy(favouriteBoards = setOf(TestSeed.BOARD, TestSeed.VIDEO_BOARD)) }
    }

    private val dice = "Random thread. Hold to choose boards"
    private val nothingToRoll = "No thread to roll. Check the boards the dice may use."

    private fun openRollFrom() {
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        composeRule.nodeWithContentDescription(dice).performTouchInput { longClick() }
        composeRule.waitForText("Roll from")
    }

    /** A board row inside the "Roll from" menu; the tab strip carries the same labels. */
    private fun vetoRow(board: String) =
        composeRule.onNode(hasText("/$board/", substring = false) and hasAnyAncestor(isPopup()))

    @Test
    fun diceTap_opensAThreadFromAFavourite() {
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        composeRule.tapIcon(dice)
        // Stickies and closed threads never come up, so /g/ can only land on the seeded thread.
        composeRule.waitUntilTrue { composeRule.hasText(TestSeed.OP_TEXT) || composeRule.hasText(TestSeed.VIDEO_OP_TEXT) }
    }

    @Test
    fun vetoEveryBoard_thenRoll_showsNothingToRoll() {
        openRollFrom()
        vetoRow(TestSeed.BOARD).performClick()
        vetoRow(TestSeed.VIDEO_BOARD).performClick()
        composeRule.nodeWithContentDescription(dice).performClick()
        composeRule.waitForText(nothingToRoll)
    }

    @Test
    fun catalogFailure_showsNothingToRoll() {
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        fakes.catalog.failWith = NetworkError.Offline
        composeRule.tapIcon(dice)
        composeRule.waitForText(nothingToRoll)
    }
}

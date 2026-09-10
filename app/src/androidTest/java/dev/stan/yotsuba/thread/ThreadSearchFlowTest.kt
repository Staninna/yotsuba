package dev.stan.yotsuba.thread

import androidx.compose.ui.test.hasSetTextAction
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.clearField
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.openSeededThread
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.typeInField
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Test

/** The in-thread search bar: the counter, stepping through matches, and both ways out. */
@HiltAndroidTest
class ThreadSearchFlowTest : FlowTest() {

    /** Opens the bar from the overflow menu and types [query]; "seeded" hits the OP and the first reply. */
    private fun search(query: String = "seeded") {
        composeRule.openSeededThread()
        composeRule.tapMenuItem("Search in thread")
        composeRule.waitUntilTrue {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.typeInField(query)
    }

    @Test
    fun searchInThread_countsMatches_andStepsBothWaysWithWrapping() {
        search()
        composeRule.waitForText("1/2", substring = false)

        composeRule.tapIcon("Next match")
        composeRule.waitForText("2/2", substring = false)
        // Forward off the end wraps to the first match.
        composeRule.tapIcon("Next match")
        composeRule.waitForText("1/2", substring = false)
        // And backward off the start wraps to the last.
        composeRule.tapIcon("Previous match")
        composeRule.waitForText("2/2", substring = false)
    }

    @Test
    fun queryWithNoMatch_showsZeroOfZero() {
        search("zzz-nothing-matches-this")
        composeRule.waitForText("0/0", substring = false)

        // A real query brings the counter back, so the field is still live.
        composeRule.clearField()
        composeRule.typeInField("seeded")
        composeRule.waitForText("1/2", substring = false)
    }

    @Test
    fun closeButton_hidesTheBarAndDropsTheQuery() {
        search()
        composeRule.waitForText("1/2", substring = false)
        composeRule.tapIcon("Close search")
        composeRule.waitForTextGone("1/2", substring = false)
        assertNoSearchField()
    }

    @Test
    fun systemBack_closesTheBarBeforeLeavingTheThread() {
        search()
        composeRule.waitForText("1/2", substring = false)
        pressBack()
        composeRule.waitForTextGone("1/2", substring = false)
        assertNoSearchField()
        // Still in the thread: back only took the search bar.
        composeRule.waitForText(TestSeed.OP_TEXT)
    }

    private fun assertNoSearchField() {
        composeRule.waitUntilTrue {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()
        }
    }
}

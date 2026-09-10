package dev.stan.yotsuba.catalog

import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.openBoardsTab
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.typeInField
import dev.stan.yotsuba.waitForText
import org.junit.Test

@HiltAndroidTest
class CatalogStatesFlowTest : FlowTest() {

    /** The board row, without waiting for a thread: these catalogs have none to wait for. */
    private fun openBoard() {
        composeRule.openBoardsTab()
        composeRule.tap(TestSeed.BOARD_TITLE)
    }

    @Test
    fun everyNetworkError_showsItsOwnMessage_andRetryRecovers() {
        fakes.catalog.failWith = NetworkError.Offline
        openBoard()
        composeRule.waitForText("You're offline")

        listOf(
            NetworkError.Timeout to "The request timed out",
            NetworkError.RateLimited to "Slow down, too many requests",
            NetworkError.NotFound to "Not found",
            NetworkError.Server(500) to "Server error (500)",
            NetworkError.Unknown() to "Something went wrong",
        ).forEach { (error, message) ->
            fakes.catalog.failWith = error
            composeRule.tap("Retry", substring = false)
            composeRule.waitForText(message)
        }

        fakes.catalog.failWith = null
        composeRule.tap("Retry", substring = false)
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
    }

    @Test
    fun boardWithNoThreads_showsTheEmptyState() {
        fakes.catalog.catalogs[TestSeed.BOARD] = emptyList()
        openBoard()
        composeRule.waitForText("No threads")
        composeRule.waitForText("This board has no threads right now.")
    }

    @Test
    fun searchWithNoMatches_namesTheQuery() {
        openBoard()
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        composeRule.tapIcon("Search")
        composeRule.waitForText("Search this catalog")
        composeRule.typeInField("zzz")
        composeRule.waitForText("No matches")
        composeRule.waitForText("Nothing matches \"zzz\".")
    }
}

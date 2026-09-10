package dev.stan.yotsuba.thread

import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.Filter
import dev.stan.yotsuba.domain.model.FilterAction
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.goBack
import dev.stan.yotsuba.openCatalog
import dev.stan.yotsuba.openSeededThread
import dev.stan.yotsuba.openThreadsTab
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import org.junit.Test

/** What the thread screen shows when the thread does not simply load. */
@HiltAndroidTest
class ThreadStatesFlowTest : FlowTest() {

    private val key = TestSeed.BOARD to TestSeed.THREAD_NO

    @Test
    fun everyNetworkFailure_saysWhatWentWrong_andOffersRetry() {
        composeRule.openCatalog()
        listOf(
            NetworkError.Offline to "You're offline",
            NetworkError.Timeout to "The request timed out",
            NetworkError.RateLimited to "Slow down, too many requests",
            NetworkError.Server(500) to "Server error (500)",
            NetworkError.NotFound to "Not found",
            NetworkError.Unknown() to "Something went wrong",
        ).forEach { (error, message) ->
            fakes.threads.failWith = error
            composeRule.tap(TestSeed.THREAD_SUBJECT)
            composeRule.waitForText(message)
            composeRule.waitForText("Retry")
            composeRule.goBack()
            composeRule.waitForText(TestSeed.CLOSED_SUBJECT)
        }
    }

    @Test
    fun retry_loadsTheThreadOnceTheNetworkIsBack() {
        fakes.threads.failWith = NetworkError.Offline
        composeRule.openCatalog()
        composeRule.tap(TestSeed.THREAD_SUBJECT)
        composeRule.waitForText("You're offline")

        fakes.threads.failWith = null
        composeRule.tap("Retry")
        composeRule.waitForText(TestSeed.OP_TEXT)
    }

    @Test
    fun aFailedRefresh_keepsTheThread_andSaysSoOnce() {
        composeRule.openSeededThread()
        fakes.threads.failWith = NetworkError.RateLimited

        composeRule.tapIcon("Refresh")
        composeRule.waitForText("Refresh failed: Slow down, too many requests")
        composeRule.listNode(TestSeed.OP_TEXT)
    }

    @Test
    fun aThreadThatDiesUnderTheReader_saysItIsArchived() {
        composeRule.openSeededThread()
        fakes.threads.failWith = NetworkError.NotFound

        composeRule.tapIcon("Refresh")
        composeRule.waitForText("Thread archived")
    }

    @Test
    fun aThreadOnlyTheArchiveHas_saysWhereItCameFrom() {
        fakes.history.seed(
            TestSeed.historyEntry(
                threadNo = TestSeed.ARCHIVED_THREAD_NO,
                subject = TestSeed.ARCHIVED_SUBJECT,
                opExcerpt = TestSeed.ARCHIVED_OP_TEXT,
            ),
        )
        composeRule.openThreadsTab("Recent")
        composeRule.tap(TestSeed.ARCHIVED_SUBJECT)

        composeRule.waitForText(TestSeed.ARCHIVED_OP_TEXT)
        composeRule.waitForText("Archived copy from desuarchive.org")
    }

    @Test
    fun withNothingElseAnswering_theVaultsOwnCopyIsShown() {
        fakes.vault.sidecars[key] = threadOf(
            listOf(TestSeed.post(TestSeed.BOARD, TestSeed.THREAD_NO, SIDECAR_TEXT, isOp = true, subject = TestSeed.THREAD_SUBJECT)),
        )
        fakes.threads.failWith = NetworkError.Offline
        composeRule.openCatalog()
        composeRule.tap(TestSeed.THREAD_SUBJECT)

        composeRule.waitForText(SIDECAR_TEXT)
        // The copy is dated from its newest post when no save in it is newer.
        composeRule.waitForText("Offline copy from")
    }

    @Test
    fun aStubbedPost_countsInTheTopBar_andOpensOnTap() {
        fakes.settings.set {
            it.copy(filters = listOf(Filter(id = "f1", pattern = PATTERN, action = FilterAction.STUB)))
        }
        composeRule.openSeededThread()

        composeRule.waitForText("Filtered: $PATTERN")
        composeRule.waitForText("1 post filtered")
        composeRule.waitForTextGone(TestSeed.SPOILER_REPLY_TEXT)

        composeRule.tap("Filtered: $PATTERN")
        composeRule.waitForText(TestSeed.SPOILER_REPLY_TEXT)
    }

    private companion object {
        const val SIDECAR_TEXT = "The copy the vault kept of this thread"
        /** Matches the seeded spoiler reply's body, and nothing else in the thread. */
        const val PATTERN = "surprise picture"
    }
}

package dev.stan.yotsuba

import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.di.TestSeed
import org.junit.Test

@HiltAndroidTest
class HistoryFlowTest : FlowTest() {

    @Test
    fun visitingThread_recordsHistoryEntry() {
        composeRule.openSeededThread()

        // Leave the thread, then open the Recent segment of the Threads tab.
        composeRule.openThreadsTab("Recent")

        // The visited thread shows up as a history entry.
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
    }
}

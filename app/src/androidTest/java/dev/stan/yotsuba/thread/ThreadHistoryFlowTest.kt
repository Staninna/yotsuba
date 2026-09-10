package dev.stan.yotsuba.thread

import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.backToTabs
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.openSeededThread
import dev.stan.yotsuba.openThreadsTab
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What reading a thread leaves behind: a history entry and a reading position. */
@HiltAndroidTest
class ThreadHistoryFlowTest : FlowTest() {

    private val key = TestSeed.BOARD to TestSeed.THREAD_NO

    @Test
    fun openingAThread_recordsIt() {
        composeRule.openSeededThread()

        composeRule.waitUntilTrue { fakes.history.state.value.isNotEmpty() }
        val entry = fakes.history.state.value.single()
        assertEquals(key, entry.board to entry.threadNo)
        assertEquals(TestSeed.THREAD_SUBJECT, entry.subject)
        assertEquals(TestSeed.OP_TEXT, entry.opExcerpt)
    }

    @Test
    fun scrolling_writesDownTheReadingPosition() {
        fakes.threads.threads[key] = longThread(FILLERS)
        composeRule.openSeededThread()
        composeRule.listNode(fillerText(FILLERS))

        composeRule.waitUntilTrue { fakes.history.scrollPositions[key] != null }
        assertTrue(fakes.history.scrollPositions.getValue(key) > TestSeed.THREAD_NO + 100)
    }

    @Test
    fun reopeningFromRecent_landsWhereTheReaderLeftOff() {
        fakes.threads.threads[key] = longThread(FILLERS)
        composeRule.openSeededThread()
        composeRule.listNode(fillerText(FILLERS))
        composeRule.waitUntilTrue { fakes.history.scrollPositions[key] != null }
        val left = fakes.history.scrollPositions.getValue(key)

        composeRule.backToTabs()
        composeRule.openThreadsTab("Recent")
        composeRule.tap(TestSeed.THREAD_SUBJECT)

        // The thread opens at the post that was at the top, not at the OP.
        composeRule.waitForText(fillerText((left - TestSeed.THREAD_NO - 100).toInt()))
        composeRule.waitForTextGone(TestSeed.OP_TEXT)
    }

    @Test
    fun withHistoryOff_theThreadLeavesNoTrace() {
        fakes.settings.set { it.copy(recordHistory = false) }
        composeRule.openSeededThread()
        composeRule.listNode(TestSeed.REPLY_TEXT)

        composeRule.waitForIdle()
        assertTrue(fakes.history.state.value.isEmpty())
    }

    private companion object {
        const val FILLERS = 40
    }
}

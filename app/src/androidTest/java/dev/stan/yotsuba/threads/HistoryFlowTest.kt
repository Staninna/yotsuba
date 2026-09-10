package dev.stan.yotsuba.threads

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.backToTabs
import dev.stan.yotsuba.clearField
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.nodeWithContentDescription
import dev.stan.yotsuba.nodeWithText
import dev.stan.yotsuba.openSeededThread
import dev.stan.yotsuba.openThreadsTab
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.tapIcon
import dev.stan.yotsuba.typeInField
import dev.stan.yotsuba.waitForText
import dev.stan.yotsuba.waitForTextGone
import dev.stan.yotsuba.waitUntilTrue
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

/** The Recent segment. One entry per date bucket, pinned to local midnight so the sections are stable. */
@HiltAndroidTest
class HistoryFlowTest : FlowTest() {

    private val startOfToday =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val day = 86_400_000L

    private val today = TestSeed.historyEntry(
        subject = TestSeed.THREAD_SUBJECT,
        viewedAt = System.currentTimeMillis(),
        lastScrollPostNo = TestSeed.THREAD_NO + 7,
    )
    private val yesterday = TestSeed.historyEntry(
        threadNo = 2_002L,
        subject = "Yesterday thread",
        opExcerpt = "yesterday body",
        viewedAt = startOfToday - 3_600_000L,
    )
    private val thisWeek = TestSeed.historyEntry(
        board = TestSeed.VIDEO_BOARD,
        threadNo = 3_002L,
        subject = "Midweek thread",
        opExcerpt = "midweek body",
        viewedAt = startOfToday - 3 * day,
    )
    private val older = TestSeed.historyEntry(
        threadNo = 4_002L,
        subject = "Ancient thread",
        opExcerpt = "ancient body",
        viewedAt = startOfToday - 30 * day,
    )

    override fun seed() {
        fakes.history.seed(today, yesterday, thisWeek, older)
    }

    private val entries get() = fakes.history.state.value

    private fun openRecent() {
        composeRule.openThreadsTab("Recent")
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
    }

    @Test
    fun entries_areGroupedByDate() {
        openRecent()
        composeRule.waitForText("Today", substring = false)
        composeRule.waitForText("Yesterday", substring = false)
        composeRule.waitForText("This week", substring = false)
        composeRule.waitForText("Older", substring = false)
        composeRule.waitForText("Ancient thread")
        composeRule.waitForText("/${TestSeed.VIDEO_BOARD}/ · ")
    }

    @Test
    fun search_matchesTitlesAndBoards_thenReportsNoMatches() {
        openRecent()
        composeRule.tapIcon("Search history")
        composeRule.waitForText("Subject or board")
        composeRule.typeInField("Ancient")
        composeRule.waitForTextGone("Yesterday thread")
        composeRule.waitForText("Ancient thread")

        composeRule.clearField()
        composeRule.typeInField("/${TestSeed.VIDEO_BOARD}/")
        composeRule.waitForText("Midweek thread")
        composeRule.waitForTextGone("Ancient thread")

        composeRule.clearField()
        composeRule.typeInField("zzz")
        composeRule.waitForText("No matches")
        composeRule.waitForText("Nothing matches \"zzz\".")

        // Closing search drops the query and the whole list is back.
        composeRule.tapIcon("Close search")
        composeRule.waitForText("Ancient thread")
    }

    @Test
    fun cardTap_reopensTheThreadWhereItWasLeft() {
        openRecent()
        composeRule.tap(TestSeed.THREAD_SUBJECT)
        // The entry's lastScrollPostNo is the final post, so the thread opens at the end:
        // the last post is composed and the OP, a screenful above it, is not.
        composeRule.waitForText(TestSeed.GREENTEXT_LINE)
        composeRule.waitForTextGone(TestSeed.OP_TEXT)
    }

    /**
     * Only the removal. The snackbar's Undo does put the entry back, but the row is restored
     * still dismissed and deletes itself again (see the report on SwipeToDeleteRow), so the
     * entry does not survive and nothing comes back on screen. Once that is fixed, tapping
     * Undo and waiting for the row belongs here.
     */
    @Test
    fun swipeToDelete_removesTheEntry() {
        openRecent()
        composeRule.nodeWithText("Yesterday thread").performTouchInput { swipeLeft() }
        composeRule.waitForText("Removed from history")
        composeRule.waitUntilTrue { entries.none { it.threadNo == yesterday.threadNo } }
        composeRule.waitForTextGone("Yesterday thread")
    }

    @Test
    fun clearAll_asksFirst_andCancelKeepsEverything() {
        openRecent()
        composeRule.tapIcon("Clear all")
        composeRule.waitForText("Clear all history?")
        composeRule.tap("Cancel")
        composeRule.waitForTextGone("Clear all history?")
        composeRule.waitForText("Ancient thread")

        composeRule.tapIcon("Clear all")
        composeRule.waitForText("Your entire reading history will be deleted. This can't be undone.")
        // Exact match hits the dialog's confirm button, not its title.
        composeRule.tap("Clear all", substring = false)
        composeRule.waitForText("No history")
        composeRule.waitUntilTrue { entries.isEmpty() }
    }

    @Test
    fun recordingOff_pausesWithRowsAndSaysSoWithout() {
        fakes.settings.set { it.copy(recordHistory = false) }
        openRecent()
        composeRule.waitForText("History is paused")
        composeRule.waitForText("New threads aren't recorded until \"Record history\" is back on in Settings.")

        fakes.history.seed()
        composeRule.waitForText("History is off")
        composeRule.waitForText("Enable \"Record history\" in Settings to keep a reading trail.")
    }

    @Test
    fun emptyHistory_disablesSearchAndClear() {
        fakes.history.seed()
        composeRule.openThreadsTab("Recent")
        composeRule.waitForText("No history")
        composeRule.nodeWithContentDescription("Search history").assertIsNotEnabled()
        composeRule.nodeWithContentDescription("Clear all").assertIsNotEnabled()
    }

    @Test
    fun readingAThread_recordsItUnderToday() {
        fakes.history.seed()
        composeRule.openSeededThread()
        composeRule.backToTabs()
        composeRule.openThreadsTab("Recent")
        composeRule.waitForText("Today", substring = false)
        composeRule.waitForText(TestSeed.THREAD_SUBJECT)
        composeRule.waitUntilTrue { entries.any { it.threadNo == TestSeed.THREAD_NO } }
    }
}

package dev.stan.yotsuba.stats

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasTextExactly
import dagger.hilt.android.testing.HiltAndroidTest
import dev.stan.yotsuba.FlowTest
import dev.stan.yotsuba.core.util.FileSize
import dev.stan.yotsuba.core.util.TimeFormat
import dev.stan.yotsuba.di.TestSeed
import dev.stan.yotsuba.domain.model.UsageEvent
import dev.stan.yotsuba.domain.model.UsageKind
import dev.stan.yotsuba.hasText
import dev.stan.yotsuba.openSettingsSection
import dev.stan.yotsuba.tap
import dev.stan.yotsuba.waitForText
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Test

/** The You page, reached the only way a user can: Settings, About, "Your numbers". */
@HiltAndroidTest
class StatsFlowTest : FlowTest() {

    /** A fixed local Tuesday afternoon, so the busiest hour and day are known. */
    private val firstDay = LocalDateTime.of(2024, 3, 5, 14, 30)
    private val firstUse = firstDay.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val nextDay = firstDay.plusDays(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun event(kind: UsageKind, board: String? = null, threadNo: Long? = null, value: Long? = null, at: Long = firstUse) =
        UsageEvent(kind, at, board, threadNo, value)

    override fun seed() {
        fakes.history.seed(TestSeed.historyEntry(), TestSeed.historyEntry(threadNo = 2_003L, subject = "Another"))
        fakes.bookmarks.seed(TestSeed.bookmark())
        fakes.vault.seed(TestSeed.vaultEntry())
        fakes.usage.seed(
            // The same thread twice counts once; a second board makes it two threads read.
            event(UsageKind.THREAD_VISITED, TestSeed.BOARD, TestSeed.THREAD_NO),
            event(UsageKind.THREAD_VISITED, TestSeed.BOARD, TestSeed.THREAD_NO),
            event(UsageKind.THREAD_VISITED, TestSeed.VIDEO_BOARD, TestSeed.VIDEO_THREAD_NO),
            event(UsageKind.READ_MARK), event(UsageKind.READ_MARK), event(UsageKind.READ_MARK),
            event(UsageKind.BOARD_OPENED, TestSeed.BOARD),
            event(UsageKind.BOARD_OPENED, TestSeed.BOARD),
            event(UsageKind.BOARD_OPENED, TestSeed.VIDEO_BOARD),
            event(UsageKind.BOOKMARK_ADDED, TestSeed.BOARD, TestSeed.THREAD_NO),
            event(UsageKind.IMAGE_SAVED, value = 1_000L),
            event(UsageKind.IMAGE_SAVED, value = 1_000L),
            event(UsageKind.VIDEO_SAVED, value = 2_000L),
            event(UsageKind.ARCHIVE_RESCUE),
            event(UsageKind.OFFLINE_COPY), event(UsageKind.OFFLINE_COPY),
            event(UsageKind.SEARCH_RUN), event(UsageKind.SEARCH_RUN), event(UsageKind.SEARCH_RUN),
            // A second consecutive day: the streak is two.
            event(UsageKind.SEARCH_RUN, at = nextDay),
        )
    }

    private fun openStats() {
        composeRule.openSettingsSection("About", "Your numbers")
        composeRule.tap("Your numbers")
        composeRule.waitForText("You", substring = false)
    }

    /** A row's value, matched through its label, since the two are separate Texts in one Row. */
    private fun assertStat(label: String, value: String) {
        composeRule.onNode(
            hasTextExactly(label) and hasAnySibling(hasTextExactly(value)),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun everyRow_showsTheNumberTheEventsAddUpTo() {
        openStats()
        composeRule.waitForText("Counted on this phone since it was installed.")
        assertStat("Threads read", "2")
        assertStat("Read marks", "3")
        assertStat("In history now", "2")
        assertStat("Watching now", "1")
        assertStat("Threads watched, ever", "1")
        assertStat("/${TestSeed.BOARD}/", "2")
        assertStat("/${TestSeed.VIDEO_BOARD}/", "1")
    }

    @Test
    fun savingAndHabitRows_showSizesAndTimes() {
        openStats()
        assertStat("Images saved", "2")
        assertStat("Videos saved", "1")
        assertStat("Downloaded into the vault", FileSize.format(4_000L))
        assertStat("In the vault now", "1 files, " + FileSize.format(12_345L))
        assertStat("Busiest hour", "14:00")
        assertStat("Busiest day", firstDay.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()))
        assertStat("Longest streak", "2 days")
        assertStat("First use", TimeFormat.date(firstUse))
        assertStat("Threads rescued from an archive", "1")
        assertStat("Offline copies opened", "2")
        assertStat("Reverse image searches", "3")
    }

    @Test
    fun sectionHeaders_nameEveryGroup() {
        openStats()
        composeRule.waitForText("Reading", substring = false)
        composeRule.waitForText("Boards by visits", substring = false)
        composeRule.waitForText("Saving", substring = false)
        composeRule.waitForText("Habits", substring = false)
        composeRule.waitForText("Elsewhere", substring = false)
    }

    @Test
    fun withoutEvents_theOptionalRowsAreLeftOut() {
        fakes.usage.seed()
        openStats()
        composeRule.waitForText("Reading", substring = false)
        assertStat("Threads read", "0")
        assertStat("Longest streak", "0 days")
        assertFalse(composeRule.hasText("Boards by visits"))
        assertFalse(composeRule.hasText("Busiest hour"))
        assertFalse(composeRule.hasText("Busiest day"))
        assertFalse(composeRule.hasText("First use"))
    }
}

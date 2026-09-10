package dev.stan.yotsuba.stats

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
import dev.stan.yotsuba.shell.nodeOnLineWith
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
            // The same thread twice counts once for threads read, but twice for the board's
            // visits: wave 12 ranks boards by thread visits rather than by catalog fetches.
            event(UsageKind.THREAD_VISITED, TestSeed.BOARD, TestSeed.THREAD_NO),
            event(UsageKind.THREAD_VISITED, TestSeed.BOARD, TestSeed.THREAD_NO),
            event(UsageKind.THREAD_VISITED, TestSeed.VIDEO_BOARD, TestSeed.VIDEO_THREAD_NO),
            event(UsageKind.READ_MARK), event(UsageKind.READ_MARK), event(UsageKind.READ_MARK),
            event(UsageKind.BOOKMARK_ADDED, TestSeed.BOARD, TestSeed.THREAD_NO),
            event(UsageKind.IMAGE_SAVED, value = 1_000L),
            event(UsageKind.IMAGE_SAVED, value = 1_000L),
            event(UsageKind.VIDEO_SAVED, value = 2_000L),
            event(UsageKind.ARCHIVE_RESCUE),
            event(UsageKind.OFFLINE_COPY), event(UsageKind.OFFLINE_COPY),
            event(UsageKind.SEARCH_RUN), event(UsageKind.SEARCH_RUN), event(UsageKind.SEARCH_RUN),
            // Bytes off the wire, one batch against a board nothing else here opens (so its
            // row cannot be confused with a "boards by visits" one) and one against no board.
            event(UsageKind.BYTES_FETCHED, TestSeed.NSFW_BOARD, value = 2_000L),
            event(UsageKind.BYTES_FETCHED, value = 1_000L),
            // A second consecutive day makes the streak two, and a fourth search.
            event(UsageKind.SEARCH_RUN, at = nextDay),
        )
    }

    private fun openStats() {
        composeRule.openSettingsSection("About", "Your numbers")
        composeRule.tap("Your numbers")
        composeRule.waitForText("You", substring = false)
    }

    /** A row's value, found on the label's line: the page scrolls, so rows below the fold count too. */
    private fun assertStat(label: String, value: String) {
        composeRule.nodeOnLineWith(label, hasTextExactly(value)).assertExists()
    }

    @Test
    fun everyRow_showsTheNumberTheEventsAddUpTo() {
        openStats()
        composeRule.waitForText("Counted on this phone, cleared and trimmed with your history.")
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
        assertStat("Reverse image searches", "4")
    }

    @Test
    fun streak_readsOneDayInTheSingular() {
        fakes.usage.seed(event(UsageKind.SEARCH_RUN))
        openStats()
        assertStat("Longest streak", "1 day")
    }

    @Test
    fun dataRows_splitTheBytesByBoard() {
        openStats()
        assertStat("Fetched over the network", FileSize.format(3_000L))
        assertStat("/${TestSeed.NSFW_BOARD}/", FileSize.format(2_000L))
        assertStat("Outside any board", FileSize.format(1_000L))
    }

    @Test
    fun vaultRow_leavesOutTheFilesARescanFoundGone() {
        fakes.vault.seed(TestSeed.vaultEntry(), TestSeed.vaultEntry(TestSeed.spoilerMediaItem).copy(absolutePath = ""))
        openStats()
        // Both files are still listed, but the one that is not on disk takes no space.
        assertStat("In the vault now", "2 files, " + FileSize.format(12_345L))
    }

    @Test
    fun sectionHeaders_nameEveryGroup() {
        openStats()
        composeRule.waitForText("Reading", substring = false)
        composeRule.waitForText("Boards by visits", substring = false)
        composeRule.waitForText("Saving", substring = false)
        composeRule.waitForText("Data", substring = false)
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

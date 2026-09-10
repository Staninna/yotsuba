package dev.stan.yotsuba.domain

import dev.stan.yotsuba.domain.model.BytesFetched
import dev.stan.yotsuba.domain.model.UsageEvent
import dev.stan.yotsuba.domain.model.UsageKind
import dev.stan.yotsuba.domain.model.UsageStats
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageStatsTest {

    private fun at(day: Int, hour: Int = 12) =
        LocalDateTime.of(2026, 3, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun `folds the event list into the page's numbers`() {
        val events = listOf(
            UsageEvent(UsageKind.THREAD_VISITED, at(2, 9), "a", 1),
            UsageEvent(UsageKind.THREAD_VISITED, at(2, 21), "a", 1),
            UsageEvent(UsageKind.THREAD_VISITED, at(3, 21), "g", 2),
            UsageEvent(UsageKind.READ_MARK, at(3, 21), "g", 2, 5),
            UsageEvent(UsageKind.READ_MARK, at(4, 21), "g", 2, 6),
            UsageEvent(UsageKind.IMAGE_SAVED, at(4), "g", 2, 100),
            UsageEvent(UsageKind.VIDEO_SAVED, at(4), "g", 2, 250),
            UsageEvent(UsageKind.BOOKMARK_ADDED, at(9, 21), "g", 2),
            UsageEvent(UsageKind.ARCHIVE_RESCUE, at(10, 21), "g", 2),
            UsageEvent(UsageKind.SEARCH_RUN, at(11, 21)),
        )
        val s = UsageStats.of(events, ZoneOffset.UTC)
        assertEquals(2, s.threadsRead)
        assertEquals(2, s.postsRead)
        // Boards rank by thread visits, not by a catalog fetch: /a/ twice, /g/ once.
        assertEquals(listOf("a" to 2, "g" to 1), s.boardsByVisits)
        assertEquals(1, s.imagesSaved)
        assertEquals(1, s.videosSaved)
        assertEquals(350L, s.bytesSaved)
        assertEquals(1, s.bookmarksAdded)
        assertEquals(1, s.archiveRescues)
        assertEquals(1, s.searchesRun)
        assertEquals(21, s.busiestHour)
        // Mar 2, 3, 4 2026 are Mon, Tue, Wed; Wednesday has three events.
        assertEquals(DayOfWeek.WEDNESDAY, s.busiestDay)
        assertEquals(3, s.longestStreak) // 2, 3, 4; then 9, 10, 11 ties and the first run stays
        assertEquals(at(2, 9), s.firstUseAt)
    }

    @Test fun `no events is all zeros and no dates`() {
        val s = UsageStats.of(emptyList(), ZoneOffset.UTC)
        assertEquals(0, s.threadsRead)
        assertEquals(0, s.longestStreak)
        assertNull(s.busiestHour)
        assertNull(s.firstUseAt)
    }

    @Test fun `bytes fetched sum per board from a starting point`() {
        val events = listOf(
            UsageEvent(UsageKind.BYTES_FETCHED, at(1), "g", value = 500),
            UsageEvent(UsageKind.BYTES_FETCHED, at(8), "g", value = 100),
            UsageEvent(UsageKind.BYTES_FETCHED, at(9), "a", value = 300),
            UsageEvent(UsageKind.BYTES_FETCHED, at(9), null, value = 20),
            UsageEvent(UsageKind.IMAGE_SAVED, at(9), "a", value = 999),
        )
        val week = BytesFetched.of(events, since = at(8))
        assertEquals(420L, week.total)
        assertEquals(listOf("a" to 300L, "g" to 100L, null to 20L), week.byBoard)
        assertEquals(920L, UsageStats.of(events, ZoneOffset.UTC).bytesFetched.total)
    }
}

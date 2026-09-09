package dev.stan.yotsuba.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** Something the user did, written by the repository that saw it. Never leaves the device. */
enum class UsageKind {
    BOARD_OPENED,
    THREAD_VISITED,
    /** The read mark rose; [UsageEvent.value] is the post it rose to. */
    READ_MARK,
    BOOKMARK_ADDED,
    /** [UsageEvent.value] is the file size in bytes. */
    IMAGE_SAVED,
    VIDEO_SAVED,
    /** A third-party archive answered for a thread 4chan had dropped. */
    ARCHIVE_RESCUE,
    SEARCH_RUN,
    /** A thread that neither 4chan nor an archive answered for was read from the vault sidecar. */
    OFFLINE_COPY,
}

data class UsageEvent(
    val kind: UsageKind,
    val at: Long,
    val board: String? = null,
    val threadNo: Long? = null,
    val value: Long? = null,
)

/** The "You" page's numbers, folded once from the whole event list. */
data class UsageStats(
    val threadsRead: Int,
    /** Read-mark advances, not a post count: the mark moves once per screenful settled on. */
    val postsRead: Int,
    /** Most opened first, at most ten. */
    val boardsByVisits: List<Pair<String, Int>>,
    val imagesSaved: Int,
    val videosSaved: Int,
    val bytesSaved: Long,
    val bookmarksAdded: Int,
    val archiveRescues: Int,
    val offlineCopies: Int,
    val searchesRun: Int,
    /** 0 to 23 in [ZoneId] local time; null before the first event. */
    val busiestHour: Int?,
    val busiestDay: DayOfWeek?,
    /** Consecutive local days with at least one event. */
    val longestStreak: Int,
    val firstUseAt: Long?,
) {
    companion object {
        fun of(events: List<UsageEvent>, zone: ZoneId): UsageStats {
            fun count(kind: UsageKind) = events.count { it.kind == kind }
            val moments = events.map { Instant.ofEpochMilli(it.at).atZone(zone) }
            val days = moments.map { it.toLocalDate() }.toSortedSet()
            var streak = 0
            var run = 0
            var previous: java.time.LocalDate? = null
            for (day in days) {
                run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
                streak = maxOf(streak, run)
                previous = day
            }
            return UsageStats(
                threadsRead = events.filter { it.kind == UsageKind.THREAD_VISITED }
                    .distinctBy { it.board to it.threadNo }.size,
                postsRead = count(UsageKind.READ_MARK),
                boardsByVisits = events.filter { it.kind == UsageKind.BOARD_OPENED }
                    .groupingBy { it.board.orEmpty() }.eachCount()
                    .toList().sortedByDescending { it.second }.take(10),
                imagesSaved = count(UsageKind.IMAGE_SAVED),
                videosSaved = count(UsageKind.VIDEO_SAVED),
                bytesSaved = events.filter { it.kind == UsageKind.IMAGE_SAVED || it.kind == UsageKind.VIDEO_SAVED }
                    .sumOf { it.value ?: 0L },
                bookmarksAdded = count(UsageKind.BOOKMARK_ADDED),
                archiveRescues = count(UsageKind.ARCHIVE_RESCUE),
                offlineCopies = count(UsageKind.OFFLINE_COPY),
                searchesRun = count(UsageKind.SEARCH_RUN),
                busiestHour = moments.groupingBy { it.hour }.eachCount().maxByOrNull { it.value }?.key,
                busiestDay = moments.groupingBy { it.dayOfWeek }.eachCount().maxByOrNull { it.value }?.key,
                longestStreak = streak,
                firstUseAt = events.minOfOrNull { it.at },
            )
        }
    }
}

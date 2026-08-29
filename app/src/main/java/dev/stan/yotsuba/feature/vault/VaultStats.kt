package dev.stan.yotsuba.feature.vault

import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultLocation

/** How many whole weeks the save history covers. */
const val STATS_WEEKS = 12

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

/** One board's share of the vault. */
data class BoardStat(val board: String, val files: Int, val bytes: Long)

/** One thread's share of the vault. */
data class ThreadStat(val location: VaultLocation, val subject: String?, val files: Int, val bytes: Long)

/**
 * Everything the statistics sheet shows, computed once from the full entry list. Pure so it
 * can be checked on the JVM with a fixed [of] clock.
 */
data class VaultStats(
    val files: Int,
    val bytes: Long,
    val images: Int,
    val videos: Int,
    val threads: Int,
    val boards: Int,
    /** Largest board first. */
    val perBoard: List<BoardStat>,
    /** Largest thread first, at most ten. */
    val biggestThreads: List<ThreadStat>,
    /**
     * Files saved in each of the last [STATS_WEEKS] seven-day windows ending at `now`, oldest
     * window first, so the last element is the week that just happened.
     */
    val savedPerWeek: List<Int>,
    val oldestSave: Long?,
    val newestSave: Long?,
) {
    val isEmpty: Boolean get() = files == 0

    companion object {
        fun of(entries: List<VaultEntry>, now: Long): VaultStats {
            val perBoard = entries
                .groupBy { it.location.board }
                .map { (board, group) -> BoardStat(board, group.size, group.totalBytes) }
                .sortedWith(compareByDescending<BoardStat> { it.bytes }.thenByDescending { it.files }.thenBy { it.board })
            val threads = entries
                .groupBy { it.location }
                .map { (location, group) ->
                    ThreadStat(location, group.firstNotNullOfOrNull { it.subject }, group.size, group.totalBytes)
                }
            val weeks = IntArray(STATS_WEEKS)
            for (entry in entries) {
                val age = now - entry.savedAt
                if (age < 0) continue
                val index = (age / WEEK_MS).toInt()
                if (index < STATS_WEEKS) weeks[STATS_WEEKS - 1 - index]++
            }
            return VaultStats(
                files = entries.size,
                bytes = entries.totalBytes,
                images = entries.count { !it.isVideo },
                videos = entries.count { it.isVideo },
                threads = threads.size,
                boards = perBoard.size,
                perBoard = perBoard,
                biggestThreads = threads
                    .sortedWith(compareByDescending<ThreadStat> { it.bytes }.thenByDescending { it.files })
                    .take(10),
                savedPerWeek = weeks.toList(),
                oldestSave = entries.minOfOrNull { it.savedAt },
                newestSave = entries.maxOfOrNull { it.savedAt },
            )
        }
    }
}

package dev.stan.yotsuba.feature.vault

import dev.stan.yotsuba.domain.model.VaultEntry

/** How many whole weeks the save history covers. */
const val STATS_WEEKS = 12

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

/**
 * Everything the statistics sheet shows, computed once from the full entry list. Pure so it
 * can be checked on the JVM with a fixed [of] clock. Boards and threads are the explorer's
 * own sections, so the sheet and the grid never disagree about what a thread holds.
 */
data class VaultStats(
    val files: Int,
    val bytes: Long,
    val images: Int,
    val videos: Int,
    val threads: Int,
    val boards: Int,
    /** Largest board first. */
    val perBoard: List<VaultBoardSection>,
    /** Largest thread first, at most ten. */
    val biggestThreads: List<VaultThreadSection>,
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
            val sections = groupByBoard(entries)
            val perBoard = sections.sortedWith(
                compareByDescending<VaultBoardSection> { it.sizeBytes }
                    .thenByDescending { it.entries.size }
                    .thenBy { it.board },
            )
            val threads = sections.flatMap { it.threads }
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
                    .sortedWith(compareByDescending<VaultThreadSection> { it.sizeBytes }.thenByDescending { it.entries.size })
                    .take(10),
                savedPerWeek = weeks.toList(),
                oldestSave = entries.minOfOrNull { it.savedAt },
                newestSave = entries.maxOfOrNull { it.savedAt },
            )
        }
    }
}

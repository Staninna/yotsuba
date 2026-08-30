package dev.stan.yotsuba.feature.vault

import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultStatsTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun entry(
        url: String,
        board: String = "g",
        threadNo: Long = 1,
        sizeBytes: Long? = 10,
        savedAt: Long = now,
        ext: String = ".jpg",
        subject: String? = null,
    ) = VaultEntry(
        url = url, location = VaultLocation(board, threadNo), subject = subject, postNo = null,
        displayName = url, absolutePath = "/vault/$url", ext = ext, sizeBytes = sizeBytes,
        width = 1, height = 1, thumbnailUrl = null, savedAt = savedAt,
    )

    @Test
    fun `empty vault has nothing`() {
        val stats = VaultStats.of(emptyList(), now)
        assertTrue(stats.isEmpty)
        assertEquals(0L, stats.bytes)
        assertNull(stats.oldestSave)
        assertEquals(List(STATS_WEEKS) { 0 }, stats.savedPerWeek)
    }

    @Test
    fun `totals count files bytes kinds threads and boards`() {
        val stats = VaultStats.of(
            listOf(
                entry("a", sizeBytes = 5),
                entry("b", sizeBytes = null, ext = ".webm"),
                entry("c", board = "a", threadNo = 2, sizeBytes = 20, savedAt = now - day),
            ),
            now,
        )
        assertEquals(3, stats.files)
        assertEquals(25L, stats.bytes)
        assertEquals(2, stats.images)
        assertEquals(1, stats.videos)
        assertEquals(2, stats.threads)
        assertEquals(2, stats.boards)
        assertEquals(now - day, stats.oldestSave)
        assertEquals(now, stats.newestSave)
    }

    @Test
    fun `boards sort by bytes descending`() {
        val stats = VaultStats.of(
            listOf(
                entry("a", board = "g", sizeBytes = 1),
                entry("b", board = "g", sizeBytes = 1),
                entry("c", board = "a", sizeBytes = 50),
                entry("d", board = "wg", sizeBytes = 7),
            ),
            now,
        )
        assertEquals(listOf("a", "wg", "g"), stats.perBoard.map { it.board })
        val g = stats.perBoard.last()
        assertEquals("g", g.board)
        assertEquals(2, g.entries.size)
        assertEquals(2L, g.sizeBytes)
    }

    @Test
    fun `biggest threads are capped at ten and carry a subject`() {
        val entries = (1L..12L).map { n -> entry("t$n", threadNo = n, sizeBytes = n, subject = "s$n") }
        val stats = VaultStats.of(entries, now)
        assertEquals(10, stats.biggestThreads.size)
        assertEquals(12L, stats.biggestThreads.first().location.threadNo)
        assertEquals("s12", stats.biggestThreads.first().subject)
        assertEquals(3L, stats.biggestThreads.last().location.threadNo)
    }

    @Test
    fun `weekly buckets end at now with the newest week last`() {
        val stats = VaultStats.of(
            listOf(
                entry("a", savedAt = now),
                entry("b", savedAt = now - 6 * day),
                entry("c", savedAt = now - 7 * day),
                entry("d", savedAt = now - 13 * day),
                entry("e", savedAt = now - 83 * day),
                entry("f", savedAt = now - 84 * day),
                entry("g", savedAt = now + day),
            ),
            now,
        )
        val expected = MutableList(STATS_WEEKS) { 0 }
        expected[STATS_WEEKS - 1] = 2
        expected[STATS_WEEKS - 2] = 2
        expected[0] = 1
        assertEquals(expected, stats.savedPerWeek)
    }
}

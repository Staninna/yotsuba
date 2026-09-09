package dev.stan.yotsuba.feature.catalog

import dev.stan.yotsuba.domain.model.CatalogSort
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.PostText
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogSortingTest {

    private fun thread(no: Long, replies: Int = 0, images: Int = 0, createdAt: Long = 0) = CatalogThread(
        board = "g", no = no, subject = null, excerpt = PostText.Empty, thumbnailUrl = null,
        replyCount = replies, imageCount = images, lastModified = 0, sticky = false, closed = false,
        createdAt = createdAt,
    )

    // Bump order 1, 2, 3. Thread 2 is oldest with the most replies; 3 is an hour old with 30.
    private val hour = 3600L
    private val now = 100 * hour
    private val threads = listOf(
        thread(1, replies = 20, images = 5, createdAt = now - 10 * hour),
        thread(2, replies = 50, images = 1, createdAt = now - 50 * hour),
        thread(3, replies = 30, images = 5, createdAt = now - hour),
    )

    private fun order(sort: CatalogSort) = threads.sortedBy(sort, now).map { it.no }

    @Test fun `each sort orders descending on its number and keeps bump order on ties`() {
        assertEquals(listOf(1L, 2L, 3L), order(CatalogSort.BUMP_ORDER))
        assertEquals(listOf(3L, 1L, 2L), order(CatalogSort.CREATION_TIME))
        assertEquals(listOf(2L, 3L, 1L), order(CatalogSort.REPLY_COUNT))
        assertEquals(listOf(1L, 3L, 2L), order(CatalogSort.IMAGE_COUNT))
        assertEquals(listOf(3L, 1L, 2L), order(CatalogSort.REPLIES_PER_HOUR))
    }

    @Test fun `a thread younger than an hour is rated as if it were an hour old`() {
        assertEquals(7.0, thread(1, replies = 7, createdAt = now - 60).repliesPerHour(now), 0.0)
    }
}

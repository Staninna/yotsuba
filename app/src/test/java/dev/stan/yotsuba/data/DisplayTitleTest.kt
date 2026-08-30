package dev.stan.yotsuba.data

import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.PostSegment
import dev.stan.yotsuba.domain.model.PostText
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayTitleTest {

    private fun bookmark(subject: String?, excerpt: String) = Bookmark(
        board = "g", threadNo = 123L, subject = subject, opExcerpt = excerpt,
        thumbnailUrl = null, replyCount = 0, imageCount = 0, bookmarkedAt = 0L,
        lastCheckedAt = null, state = BookmarkState.ALIVE,
    )

    private fun history(subject: String?, excerpt: String) = HistoryEntry(
        board = "g", threadNo = 123L, subject = subject, opExcerpt = excerpt,
        thumbnailUrl = null, viewedAt = 0L, lastScrollPostNo = null,
    )

    private fun catalogThread(subject: String?, excerpt: String) = CatalogThread(
        board = "g", no = 123L, subject = subject,
        excerpt = if (excerpt.isEmpty()) PostText.Empty else PostText(listOf(PostSegment(excerpt))),
        thumbnailUrl = null, replyCount = 0, imageCount = 0, lastModified = 0L,
        sticky = false, closed = false,
    )

    @Test fun `subject wins when present`() {
        assertEquals("Subject", bookmark("Subject", "excerpt").displayTitle)
        assertEquals("Subject", history("Subject", "excerpt").displayTitle)
        assertEquals("Subject", catalogThread("Subject", "excerpt").displayTitle)
    }

    @Test fun `falls back to truncated excerpt`() {
        val long = "x".repeat(80)
        assertEquals("x".repeat(60), bookmark(null, long).displayTitle)
        assertEquals("x".repeat(60), history(null, long).displayTitle)
        assertEquals("x".repeat(60), catalogThread(null, long).displayTitle)
    }

    @Test fun `blank excerpt falls back to thread identity`() {
        assertEquals("/g/123", bookmark(null, "").displayTitle)
        assertEquals("/g/123", history(null, "").displayTitle)
        assertEquals("#123", catalogThread(null, "").displayTitle)
    }
}

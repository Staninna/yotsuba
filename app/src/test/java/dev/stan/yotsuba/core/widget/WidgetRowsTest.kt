package dev.stan.yotsuba.core.widget

import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRowsTest {

    private fun bookmark(
        no: Long,
        unread: Int = 0,
        pinned: Boolean = false,
        state: BookmarkState = BookmarkState.ALIVE,
        activity: Long = no,
        subject: String? = "t$no",
    ) = Bookmark(
        board = "g",
        threadNo = no,
        subject = subject,
        opExcerpt = "excerpt $no",
        thumbnailUrl = null,
        replyCount = 0,
        imageCount = 0,
        bookmarkedAt = no,
        lastCheckedAt = null,
        lastSeenPostNo = null,
        state = state,
        readUpTo = 0L,
        postNos = (1..unread).map { it.toLong() },
        pinned = pinned,
        lastActivityAt = activity,
    )

    @Test
    fun `unread threads come before pinned, pinned before the rest`() {
        val rows = orderForWidget(
            listOf(
                bookmark(1),
                bookmark(2, pinned = true),
                bookmark(3, unread = 2),
                bookmark(4, unread = 5, state = BookmarkState.DEAD),
            ),
        )
        assertEquals(listOf(4L, 3L, 2L, 1L), rows.map { it.threadNo })
        assertTrue(rows.first().dead)
    }

    @Test
    fun `otherwise most recent activity first`() {
        val rows = orderForWidget(listOf(bookmark(1, activity = 10), bookmark(2, activity = 30), bookmark(3, activity = 20)))
        assertEquals(listOf(2L, 3L, 1L), rows.map { it.threadNo })
    }

    @Test
    fun `row carries display title and unread`() {
        val row = orderForWidget(listOf(bookmark(7, unread = 3, subject = null))).single()
        assertEquals("excerpt 7", row.title)
        assertEquals(3, row.unread)
        assertEquals("g", row.board)
    }

}

package dev.stan.yotsuba.feature.catalog

import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.PostText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewRepliesTest {

    private fun thread(replyCount: Int, lastReplyNos: List<Long>) = CatalogThread(
        board = "g", no = 100, subject = null, excerpt = PostText(emptyList()), thumbnailUrl = null,
        replyCount = replyCount, imageCount = 0, lastModified = 0, sticky = false, closed = false,
        lastReplyNos = lastReplyNos,
    )

    @Test fun `counts the tail past the read mark and flags a saturated tail as a floor`() {
        val tail = listOf(101L, 102L, 103L, 104L, 105L)
        assertNull(thread(replyCount = 5, tail).newRepliesSince(readUpTo = 105))
        assertEquals(NewReplies(2, atLeast = false), thread(replyCount = 5, tail).newRepliesSince(readUpTo = 103))
        // Everything the catalog lists is unread and the thread has more replies than that.
        assertEquals(NewReplies(5, atLeast = true), thread(replyCount = 40, tail).newRepliesSince(readUpTo = 100))
        // Everything is unread but the tail is the whole thread: the count is exact.
        assertEquals(NewReplies(5, atLeast = false), thread(replyCount = 5, tail).newRepliesSince(readUpTo = 100))
        assertNull(thread(replyCount = 0, emptyList()).newRepliesSince(readUpTo = 100))
    }
}

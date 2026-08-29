package dev.stan.yotsuba.domain

import dev.stan.yotsuba.core.text.PostSegment
import dev.stan.yotsuba.core.text.PostText
import dev.stan.yotsuba.domain.model.PostGraph
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.backlinksOf
import org.junit.Assert.assertEquals
import org.junit.Test

class PostGraphTest {

    private fun post(no: Long, quotes: List<Long> = emptyList()) = ThreadPost(
        board = "g", no = no, isOp = no == 1L, name = "Anonymous", tripcode = null,
        capcode = null, posterId = null, countryCode = null, countryName = null,
        timeSeconds = 0, subject = null,
        body = PostText(listOf(PostSegment("post $no"))),
        media = null, quotedPostNos = quotes,
    )

    //  1 <- 2 <- 4
    //    <- 3 <- 5
    private val posts = listOf(
        post(1), post(2, listOf(1)), post(3, listOf(1)), post(4, listOf(2)), post(5, listOf(3)),
    )
    private val graph = PostGraph(posts.associateBy { it.no }, backlinksOf(posts))

    @Test fun `backlinks reverse the quote edges`() {
        assertEquals(mapOf(1L to listOf(2L, 3L), 2L to listOf(4L), 3L to listOf(5L)), backlinksOf(posts))
    }

    @Test fun `descendants walk the whole subtree in post order`() {
        assertEquals(listOf(2L, 3L, 4L, 5L), graph.descendantsOf(1).map { it.no })
        assertEquals(listOf(4L), graph.descendantsOf(2).map { it.no })
        assertEquals(emptyList<Long>(), graph.descendantsOf(4).map { it.no })
    }

    @Test fun `ancestors walk back up to the root`() {
        assertEquals(listOf(1L, 2L), graph.ancestorsOf(4).map { it.no })
        assertEquals(listOf(1L), graph.ancestorsOf(2).map { it.no })
        assertEquals(emptyList<Long>(), graph.ancestorsOf(1).map { it.no })
    }

    @Test fun `the conversation around a post is its ancestors, itself and its replies`() {
        assertEquals(listOf(1L, 2L, 4L), graph.conversationAround(4).map { it.no })
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), graph.conversationAround(1).map { it.no })
    }

    @Test fun `a quote cycle terminates instead of looping forever`() {
        val cyclic = listOf(post(1, listOf(3)), post(2, listOf(1)), post(3, listOf(2)))
        val g = PostGraph(cyclic.associateBy { it.no }, backlinksOf(cyclic))
        assertEquals(listOf(2L, 3L), g.descendantsOf(1).map { it.no })
        assertEquals(listOf(2L, 3L), g.ancestorsOf(1).map { it.no })
    }

    @Test fun `a quote pointing outside the thread is dropped, not crashed on`() {
        val crossThread = listOf(post(1, listOf(99999)), post(2, listOf(1)))
        val g = PostGraph(crossThread.associateBy { it.no }, backlinksOf(crossThread))
        assertEquals(emptyList<Long>(), g.ancestorsOf(1).map { it.no })
        assertEquals(listOf(2L), g.descendantsOf(1).map { it.no })
    }
}

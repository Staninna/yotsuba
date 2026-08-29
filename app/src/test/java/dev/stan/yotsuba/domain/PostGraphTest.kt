package dev.stan.yotsuba.domain

import dev.stan.yotsuba.core.text.PostSegment
import dev.stan.yotsuba.core.text.PostText
import dev.stan.yotsuba.domain.model.PostGraph
import dev.stan.yotsuba.domain.model.ThreadPost
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
    private val graph = PostGraph(posts, PostGraph.backlinksOf(posts))

    @Test fun `backlinks reverse the quote edges`() {
        assertEquals(mapOf(1L to listOf(2L, 3L), 2L to listOf(4L), 3L to listOf(5L)), PostGraph.backlinksOf(posts))
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
        val g = PostGraph(cyclic, PostGraph.backlinksOf(cyclic))
        assertEquals(listOf(2L, 3L), g.descendantsOf(1).map { it.no })
        assertEquals(listOf(2L, 3L), g.ancestorsOf(1).map { it.no })
    }

    @Test fun `a quote pointing outside the thread is dropped, not crashed on`() {
        val crossThread = listOf(post(1, listOf(99999)), post(2, listOf(1)))
        val g = PostGraph(crossThread, PostGraph.backlinksOf(crossThread))
        assertEquals(emptyList<Long>(), g.ancestorsOf(1).map { it.no })
        assertEquals(listOf(2L), g.descendantsOf(1).map { it.no })
    }

    @Test fun `walks follow thread order, not post number order`() {
        // Numbers out of order: 50 was posted first, then 10 quoting it, then 30 quoting 10.
        val shuffled = listOf(post(50), post(10, listOf(50)), post(30, listOf(10)))
        val g = PostGraph(shuffled, PostGraph.backlinksOf(shuffled))
        assertEquals(listOf(10L, 30L), g.descendantsOf(50).map { it.no })
        assertEquals(listOf(50L, 10L), g.ancestorsOf(30).map { it.no })
        assertEquals(listOf(50L, 10L, 30L), g.conversationAround(10).map { it.no })
    }

    @Test fun `tree nests each post under its first in-thread quote and caps the depth`() {
        val chain = listOf(
            post(1), post(2, listOf(1)), post(3, listOf(2)), post(4, listOf(3)), post(5, listOf(4)), post(6),
        )
        val g = PostGraph(chain, PostGraph.backlinksOf(chain))
        assertEquals(listOf(0, 1, 2, 3, 4, 0), g.tree().map { it.depth })
        assertEquals(listOf(0, 1, 2, 2, 2, 0), g.tree(maxDepth = 2).map { it.depth })
        assertEquals(listOf(null, 1L, 2L, 3L, 4L, null), g.tree().map { it.parentNo })
    }

    @Test fun `tree ignores forward quotes so a reply never nests under a later post`() {
        val forward = listOf(post(1), post(2, listOf(3)), post(3, listOf(1)))
        val g = PostGraph(forward, PostGraph.backlinksOf(forward))
        assertEquals(listOf(0, 0, 1), g.tree().map { it.depth })
    }
}

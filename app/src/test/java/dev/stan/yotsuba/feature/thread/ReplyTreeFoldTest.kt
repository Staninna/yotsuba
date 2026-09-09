package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.domain.model.PostGraph
import org.junit.Assert.assertEquals
import org.junit.Test

/** The preview sheet's reply tree: a folded branch keeps its row and count, loses its rows. */
class ReplyTreeFoldTest {

    //  100 <- 101 <- 102 <- 103
    //      <- 104
    private val posts = listOf(
        ThreadEnv.post(100).copy(isOp = true),
        ThreadEnv.post(101).copy(quotedPostNos = listOf(100)),
        ThreadEnv.post(102).copy(quotedPostNos = listOf(101)),
        ThreadEnv.post(103).copy(quotedPostNos = listOf(102)),
        ThreadEnv.post(104).copy(quotedPostNos = listOf(100)),
    )
    private val tree = PostGraph(posts, PostGraph.backlinksOf(posts)).replyTree(100, MAX_TREE_DEPTH)

    @Test fun `unfolded, every reply shows with its depth and descendant count`() {
        val rows = replyRows(tree, folded = emptySet())
        assertEquals(listOf(101L, 102L, 103L, 104L), rows.map { it.post.no })
        assertEquals(listOf(0, 1, 2, 0), rows.map { it.depth })
        assertEquals(listOf(2, 1, 0, 0), rows.map { it.descendants })
        assertEquals(listOf(false, false, false, false), rows.map { it.folded })
    }

    @Test fun `folding a branch cuts everything under it and nothing beside it`() {
        val rows = replyRows(tree, folded = setOf(101L))
        assertEquals(listOf(101L, 104L), rows.map { it.post.no })
        assertEquals(listOf(2, 0), rows.map { it.descendants })
        assertEquals(listOf(true, false), rows.map { it.folded })
    }

    @Test fun `a fold deeper inside a folded branch changes nothing until it is opened`() {
        assertEquals(listOf(101L, 104L), replyRows(tree, folded = setOf(101L, 102L)).map { it.post.no })
        assertEquals(listOf(101L, 102L, 104L), replyRows(tree, folded = setOf(102L)).map { it.post.no })
    }
}

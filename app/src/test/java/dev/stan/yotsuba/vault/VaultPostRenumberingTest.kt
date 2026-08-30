package dev.stan.yotsuba.vault

import dev.stan.yotsuba.core.vault.VaultPostRenumbering
import dev.stan.yotsuba.domain.model.PostAnnotation
import dev.stan.yotsuba.domain.model.PostSegment
import dev.stan.yotsuba.domain.model.PostText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultPostRenumberingTest {

    @Test fun `no plan when the source numbers are free in the target`() {
        assertTrue(VaultPostRenumbering.plan(listOf(1L, 3L), targetPostNos = listOf(2L), targetNos = listOf(4L)).isEmpty())
    }

    @Test fun `a collision moves every source number past the target's highest`() {
        val plan = VaultPostRenumbering.plan(listOf(1L, 2L, 3L), targetPostNos = listOf(1L, 2L), targetNos = listOf(5L))
        assertEquals(mapOf(1L to 6L, 2L to 7L, 3L to 8L), plan)
    }

    @Test fun `apply renumbers the post, its quotes and its quotelinks, and demotes the OP`() {
        val plan = mapOf(1L to 11L, 2L to 12L)
        val reply = vaultPost(2, quotes = listOf(1)).copy(
            body = PostText(
                listOf(
                    PostSegment(">>1", annotation = PostAnnotation.QuotelinkSameThread(1)),
                    PostSegment(" nice"),
                ),
            ),
        )

        val op = VaultPostRenumbering.apply(vaultPost(1, isOp = true), plan)
        val moved = VaultPostRenumbering.apply(reply, plan)

        assertEquals(11L, op.no)
        assertFalse(op.isOp)
        assertEquals("Cats", op.subject)
        assertEquals(12L, moved.no)
        assertEquals(listOf(11L), moved.quotedPostNos)
        assertEquals(">>11 nice", moved.body.plainText)
        assertEquals(PostAnnotation.QuotelinkSameThread(11), moved.body.segments.first().annotation)
    }

    @Test fun `an empty plan leaves the post alone`() {
        val post = vaultPost(1, isOp = true)
        assertEquals(post, VaultPostRenumbering.apply(post, emptyMap()))
    }
}

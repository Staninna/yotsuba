package dev.stan.yotsuba.feature.home

import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.PostText
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ThreadRouletteTest {

    private fun thread(board: String, no: Long, sticky: Boolean = false, closed: Boolean = false) = CatalogThread(
        board = board, no = no, subject = null, excerpt = PostText(emptyList()), thumbnailUrl = null,
        replyCount = 0, imageCount = 0, lastModified = 0, sticky = sticky, closed = closed,
    )

    private val catalogs = mapOf(
        "g" to listOf(thread("g", 1, sticky = true), thread("g", 2, closed = true)),
        "a" to listOf(thread("a", 10, sticky = true), thread("a", 11), thread("a", 12)),
        "v" to listOf(thread("v", 20)),
    )

    @Test fun `lands on a live thread of an allowed board, or nowhere`() = runTest {
        val favourites = catalogs.keys
        val fetched = mutableListOf<String>()
        val catalog: suspend (String) -> List<CatalogThread> = { fetched += it; catalogs.getValue(it) }
        repeat(20) { seed ->
            val pick = pickRandomThread(favourites - "v", Random(seed), catalog)!!
            assertEquals("a", pick.board)
            assertTrue(pick.no in listOf(11L, 12L))
        }
        assertNull(pickRandomThread(listOf("g"), Random(1), catalog))
        assertNull(pickRandomThread(emptyList(), Random(1), catalog))
        // A board that pays off ends the search; /g/ is fetched only when it comes up first.
        fetched.clear()
        pickRandomThread(listOf("v"), Random(1), catalog)
        assertEquals(listOf("v"), fetched)
    }
}

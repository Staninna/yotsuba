package dev.stan.yotsuba.feature.catalog

import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.PostText
import org.junit.Assert.assertEquals
import org.junit.Test

class CrossReferencesTest {

    private fun thread(no: Long, vararg quotes: Long) = CatalogThread(
        board = "g", no = no, subject = null, excerpt = PostText.Empty, thumbnailUrl = null,
        replyCount = 0, imageCount = 0, lastModified = 0, sticky = false, closed = false,
        quotedThreadNos = quotes.toSet(),
    )

    @Test fun `counts links both ways and ignores threads not in the catalog`() {
        val refs = crossReferences(listOf(thread(1, 2, 3, 999), thread(2, 3), thread(3), thread(4)))
        assertEquals(
            mapOf(
                1L to CrossReferences(linksTo = 2, referencedBy = 0),
                2L to CrossReferences(linksTo = 1, referencedBy = 1),
                3L to CrossReferences(linksTo = 0, referencedBy = 2),
            ),
            refs,
        )
    }
}

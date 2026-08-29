package dev.stan.yotsuba.navigation

import dev.stan.yotsuba.core.util.Urls.InternalLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavExternalLinksTest {
    @Test
    fun `view data resolves a thread link with post anchor`() {
        assertEquals(
            InternalLink.Thread("g", 123L, 456L),
            ExternalLinks.fromViewData("https://boards.4chan.org/g/thread/123#p456"),
        )
    }

    @Test
    fun `view data resolves a catalog link`() {
        assertEquals(InternalLink.Catalog("a"), ExternalLinks.fromViewData("https://boards.4chan.org/a/catalog"))
    }

    @Test
    fun `shared text picks the first 4chan url and ignores the rest`() {
        val text = "look at this https://example.com/x and https://boards.4channel.org/v/thread/77/title, wild."
        assertEquals(InternalLink.Thread("v", 77L), ExternalLinks.fromSharedText(text))
    }

    @Test
    fun `text without a 4chan url yields nothing`() {
        assertNull(ExternalLinks.fromSharedText("just words https://example.com"))
        assertNull(ExternalLinks.fromSharedText(null))
        assertNull(ExternalLinks.fromViewData(null))
    }
}

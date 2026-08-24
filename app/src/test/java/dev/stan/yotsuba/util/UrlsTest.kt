package dev.stan.yotsuba.util

import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.core.util.Urls.InternalLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlsTest {
    @Test fun `media urls`() {
        assertEquals("https://i.4cdn.org/g/1755640000123s.jpg", Urls.thumbnail("g", 1755640000123L))
        assertEquals("https://i.4cdn.org/g/1755640000123.webm", Urls.fullMedia("g", 1755640000123L, ".webm"))
    }

    @Test fun `protocol-relative board link routes to catalog`() {
        assertEquals(InternalLink.Catalog("wsr"), Urls.parseInternal("//boards.4chan.org/wsr/"))
    }

    @Test fun `https board link routes to catalog`() {
        assertEquals(InternalLink.Catalog("biz"), Urls.parseInternal("https://boards.4chan.org/biz/"))
    }

    @Test fun `thread link routes to thread`() {
        assertEquals(
            InternalLink.Thread("g", 109593884L, 109593885L),
            Urls.parseInternal("https://boards.4chan.org/g/thread/109593884#p109593885"),
        )
    }

    @Test fun `catalog search link carries the search term`() {
        assertEquals(
            InternalLink.Catalog("g", "s=lmg".removePrefix("s=")),
            Urls.parseInternal("//boards.4chan.org/g/catalog#s=lmg"),
        )
    }

    @Test fun `4channel host also internal`() {
        assertEquals(InternalLink.Catalog("po"), Urls.parseInternal("https://boards.4channel.org/po/"))
    }

    @Test fun `other hosts are external`() {
        assertNull(Urls.parseInternal("https://example.com/g/thread/1"))
        assertNull(Urls.parseInternal("https://i.4cdn.org/g/123.jpg"))
    }

    @Test fun `domain extraction`() {
        assertEquals("example.com", Urls.domainOf("https://example.com/x?y=1"))
        assertEquals("boards.4chan.org", Urls.domainOf("//boards.4chan.org/wsr/"))
    }
}

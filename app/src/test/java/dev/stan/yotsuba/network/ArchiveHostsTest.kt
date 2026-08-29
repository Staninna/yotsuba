package dev.stan.yotsuba.network

import dev.stan.yotsuba.core.network.ArchiveHosts
import dev.stan.yotsuba.domain.model.ArchiveSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveHostsTest {

    @Test fun `each board goes to its archive`() {
        assertEquals(ArchiveSource.DESU, ArchiveHosts.sourceFor("a"))
        assertEquals(ArchiveSource.DESU, ArchiveHosts.sourceFor("wsg"))
        assertEquals(ArchiveSource.B4K, ArchiveHosts.sourceFor("v"))
        assertEquals(ArchiveSource.B4K, ArchiveHosts.sourceFor("vst"))
        assertEquals(ArchiveSource.WAROSU, ArchiveHosts.sourceFor("g"))
        assertEquals(ArchiveSource.WAROSU, ArchiveHosts.sourceFor("3"))
    }

    @Test fun `a board two archives carry resolves to the first in order`() {
        assertEquals(ArchiveSource.DESU, ArchiveHosts.sourceFor("vr"))
    }

    @Test fun `an unarchived board has no source`() {
        assertNull(ArchiveHosts.sourceFor("b"))
        assertNull(ArchiveHosts.sourceFor(""))
    }

    @Test fun `foolfuuka hosts have an api url and warosu does not`() {
        assertEquals(
            "https://desuarchive.org/_/api/chan/thread/?board=a&num=123",
            ArchiveHosts.apiUrl(ArchiveSource.DESU, "a", 123),
        )
        assertEquals(
            "https://arch.b4k.co/_/api/chan/thread/?board=v&num=7",
            ArchiveHosts.apiUrl(ArchiveSource.B4K, "v", 7),
        )
        assertNull(ArchiveHosts.apiUrl(ArchiveSource.WAROSU, "g", 1))
    }

    @Test fun `thread urls open the archive's own page`() {
        assertEquals("https://desuarchive.org/a/thread/123", ArchiveHosts.threadUrl(ArchiveSource.DESU, "a", 123))
        assertEquals("https://warosu.org/g/thread/1", ArchiveHosts.threadUrl(ArchiveSource.WAROSU, "g", 1))
    }
}

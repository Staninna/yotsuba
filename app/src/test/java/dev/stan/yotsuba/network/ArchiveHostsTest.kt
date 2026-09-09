package dev.stan.yotsuba.network

import dev.stan.yotsuba.core.network.ArchiveHosts
import dev.stan.yotsuba.domain.model.ArchiveSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveHostsTest {

    @Test fun `each board lists its archives in chain order`() {
        assertEquals(listOf(ArchiveSource.DESU, ArchiveSource.ARCHIVED_MOE), ArchiveHosts.sourcesFor("a"))
        assertEquals(listOf(ArchiveSource.B4K, ArchiveSource.ARCHIVED_MOE), ArchiveHosts.sourcesFor("vst"))
        assertEquals(
            listOf(ArchiveSource.DESU, ArchiveSource.ARCHIVED_MOE, ArchiveSource.WAROSU),
            ArchiveHosts.sourcesFor("vr"),
        )
        assertEquals(listOf(ArchiveSource.ARCHIVED_MOE, ArchiveSource.WAROSU), ArchiveHosts.sourcesFor("3"))
    }

    @Test fun `an unarchived board has no source`() {
        assertEquals(emptyList<ArchiveSource>(), ArchiveHosts.sourcesFor("zzz"))
        assertEquals(emptyList<ArchiveSource>(), ArchiveHosts.sourcesFor(""))
    }

    @Test fun `foolfuuka hosts have an api url and warosu does not`() {
        assertEquals(
            "https://desuarchive.org/_/api/chan/thread/?board=a&num=123",
            ArchiveHosts.apiUrl(ArchiveSource.DESU, "a", 123),
        )
        assertEquals(
            "https://arch.b4k.dev/_/api/chan/thread/?board=v&num=7",
            ArchiveHosts.apiUrl(ArchiveSource.B4K, "v", 7),
        )
        assertNull(ArchiveHosts.apiUrl(ArchiveSource.WAROSU, "g", 1))
    }

    @Test fun `thread urls open the archive's own page`() {
        assertEquals("https://desuarchive.org/a/thread/123", ArchiveHosts.threadUrl(ArchiveSource.DESU, "a", 123))
        assertEquals("https://warosu.org/g/thread/1", ArchiveHosts.threadUrl(ArchiveSource.WAROSU, "g", 1))
    }
}

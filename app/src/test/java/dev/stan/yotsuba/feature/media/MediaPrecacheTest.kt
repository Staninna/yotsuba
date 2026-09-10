package dev.stan.yotsuba.feature.media

import dev.stan.yotsuba.core.network.NetworkStatus
import dev.stan.yotsuba.domain.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which pages get fetched ahead, and when none do. */
class MediaPrecacheTest {

    @Test fun `the window is the next count pages`() {
        assertEquals(listOf(3, 4, 5), precacheWindow(page = 2, count = 3, pageCount = 10).toList())
    }

    @Test fun `the window stops at the last page`() {
        assertEquals(listOf(8, 9), precacheWindow(page = 7, count = 3, pageCount = 10).toList())
        assertTrue(precacheWindow(page = 9, count = 3, pageCount = 10).isEmpty())
    }

    @Test fun `a count of zero fetches nothing`() {
        assertTrue(precacheWindow(page = 2, count = 0, pageCount = 10).isEmpty())
        assertTrue(precacheWindow(page = 0, count = 3, pageCount = 0).isEmpty())
    }

    @Test fun `data saver and offline hold everything back`() {
        assertEquals(0, precacheAllowance(Settings(dataSaver = true), NetworkStatus.Unmetered))
        assertEquals(0, precacheAllowance(Settings(), NetworkStatus.Offline))
    }

    @Test fun `unmetered only means nothing on mobile data unless switched off`() {
        val settings = Settings(precacheCount = 5)
        assertEquals(0, precacheAllowance(settings, NetworkStatus.Metered))
        assertEquals(5, precacheAllowance(settings, NetworkStatus.Unmetered))
        assertEquals(5, precacheAllowance(settings.copy(precacheUnmeteredOnly = false), NetworkStatus.Metered))
    }
}

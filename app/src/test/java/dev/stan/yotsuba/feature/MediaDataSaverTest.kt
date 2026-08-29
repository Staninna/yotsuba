package dev.stan.yotsuba.feature

import dev.stan.yotsuba.core.network.NetworkStatus
import dev.stan.yotsuba.feature.media.defersHeavyMedia
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDataSaverTest {

    @Test fun `data saver on a metered connection defers the heavy bytes`() {
        assertTrue(defersHeavyMedia(dataSaver = true, status = NetworkStatus.Metered))
    }

    @Test fun `anything else keeps the current behaviour`() {
        assertFalse(defersHeavyMedia(dataSaver = true, status = NetworkStatus.Unmetered))
        assertFalse(defersHeavyMedia(dataSaver = true, status = NetworkStatus.Offline))
        assertFalse(defersHeavyMedia(dataSaver = false, status = NetworkStatus.Metered))
        assertFalse(defersHeavyMedia(dataSaver = false, status = NetworkStatus.Unmetered))
    }
}

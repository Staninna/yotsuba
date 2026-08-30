package dev.stan.yotsuba.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.stan.yotsuba.core.media.MediaByteSource
import java.io.ByteArrayOutputStream
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs under Robolectric because the cache lookup needs a Context for Coil's
 * singleton image loader; nothing is in that cache here, so every request
 * exercises the network fallback path through OkHttp.
 */
@RunWith(RobolectricTestRunner::class)
class MediaByteSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: MediaByteSource

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        source = MediaByteSource(context, OkHttpClient())
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `streams body bytes from network when not cached`() {
        val payload = ByteArray(64 * 1024) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))

        val out = ByteArrayOutputStream()
        source.copyTo(server.url("/g/123.jpg").toString(), out)

        assertArrayEquals(payload, out.toByteArray())
        assertEquals("/g/123.jpg", server.takeRequest().path)
    }

    @Test fun `unsuccessful response throws IOException with code`() {
        server.enqueue(MockResponse().setResponseCode(404))
        try {
            source.copyTo(server.url("/gone.webm").toString(), ByteArrayOutputStream())
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("HTTP 404"))
        }
    }
}

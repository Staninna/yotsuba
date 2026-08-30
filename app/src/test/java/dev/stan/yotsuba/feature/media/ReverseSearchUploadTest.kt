package dev.stan.yotsuba.feature.media

import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReverseSearchUploadTest {

    @get:Rule val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var uploader: ReverseSearchUploader
    private lateinit var image: File

    @Before fun setUp() {
        server.start()
        val base = server.url("/").toString().removeSuffix("/")
        uploader = ReverseSearchUploader(
            OkHttpClient(),
            UploadEndpoints(
                tineye = "$base/tineye/result_json/",
                tineyeResults = "$base/tineye/search/",
                yandexUpload = "$base/yandex/upload",
                yandexResults = "$base/yandex/results?rpt=imageview&cbir_id=",
                litterbox = "$base/litterbox",
                zeroXZero = "$base/0x0",
            ),
        )
        image = tmp.newFile("frame.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `tineye query hash becomes the results page`() = runTest {
        server.enqueue(MockResponse().setBody("""{"page": 1, "query_hash": "abc123", "num_matches": 0}"""))
        val url = uploader.directSearchUrl(ReverseSearchEngine.TINEYE, image, ".jpg").getOrThrow()
        assertTrue(url, url.endsWith("/tineye/search/abc123"))
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue(body, body.contains("name=\"image\""))
        assertTrue(body, body.contains("Content-Type: image/jpeg"))
    }

    @Test fun `tineye answering a page instead of json is a failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>results</html>"))
        assertTrue(uploader.directSearchUrl(ReverseSearchEngine.TINEYE, image, ".jpg").isFailure)
    }

    @Test fun `yandex gets the raw bytes and its cbir id opens results`() = runTest {
        server.enqueue(MockResponse().setBody("""{"cbir_id":"123/abc","namespace":"images-cbir","sizes":{}}"""))
        val url = uploader.directSearchUrl(ReverseSearchEngine.YANDEX, image, ".jpg").getOrThrow()
        assertTrue(url, url.endsWith("/yandex/results?rpt=imageview&cbir_id=123%2Fabc"))
        val recorded = server.takeRequest()
        assertEquals("image/jpeg", recorded.getHeader("Content-Type"))
        assertEquals(3, recorded.bodySize)
    }

    @Test fun `garbage from yandex is a failure, not a crash`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        assertTrue(uploader.directSearchUrl(ReverseSearchEngine.YANDEX, image, ".jpg").isFailure)
    }

    @Test fun `engines without a form refuse the direct route`() = runTest {
        assertTrue(uploader.directSearchUrl(ReverseSearchEngine.SAUCENAO, image, ".jpg").isFailure)
        assertTrue(uploader.directSearchUrl(ReverseSearchEngine.IQDB, image, ".jpg").isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test fun `litterbox takes the file for an hour`() = runTest {
        server.enqueue(MockResponse().setBody("https://litter.catbox.moe/abc.jpg"))
        val url = uploader.hostTemporarily(image, ".jpg").getOrThrow()
        assertEquals("https://litter.catbox.moe/abc.jpg", url)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("name=\"reqtype\""))
        assertTrue(body, body.contains("fileupload"))
        assertTrue(body, body.contains("name=\"time\""))
        assertTrue(body, body.contains("1h"))
        assertTrue(body, body.contains("name=\"fileToUpload\""))
    }

    @Test fun `a failed litterbox upload falls through to 0x0`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        server.enqueue(MockResponse().setBody("https://0x0.st/abcd.jpg"))
        val url = uploader.hostTemporarily(image, ".jpg").getOrThrow()
        assertEquals("https://0x0.st/abcd.jpg", url)
        server.takeRequest()
        val fallback = server.takeRequest()
        assertTrue(fallback.getHeader("User-Agent").orEmpty().startsWith("Yotsuba/"))
        assertEquals("24", fallback.getHeader("X-Expires"))
        assertTrue(fallback.body.readUtf8().contains("name=\"file\""))
    }

    @Test fun `a host answering with something that is not a URL is a failure`() = runTest {
        server.enqueue(MockResponse().setBody("<html>error</html>"))
        server.enqueue(MockResponse().setBody("also not a url"))
        assertTrue(uploader.hostTemporarily(image, ".jpg").isFailure)
    }
}

package dev.stan.yotsuba.data

import dev.stan.yotsuba.data.repository.ReverseSearchRepositoryImpl
import dev.stan.yotsuba.data.repository.UploadEndpoints
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.repository.DirectUploadEngine
import dev.stan.yotsuba.domain.repository.TemporaryHost
import java.io.File
import kotlinx.coroutines.Dispatchers
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

class ReverseSearchRepositoryImplTest {

    @get:Rule val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var repo: ReverseSearchRepositoryImpl
    private lateinit var image: File

    @Before fun setUp() {
        server.start()
        val base = server.url("/").toString().removeSuffix("/")
        repo = ReverseSearchRepositoryImpl(
            Dispatchers.Unconfined,
            UploadEndpoints(
                tineye = "$base/tineye/result_json/",
                tineyeResults = "$base/tineye/search/",
                yandexUpload = "$base/yandex/upload",
                yandexResults = "$base/yandex/results?rpt=imageview&cbir_id=",
                litterbox = "$base/litterbox",
                zeroXZero = "$base/0x0",
            ),
            OkHttpClient(),
        )
        image = tmp.newFile("frame.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }

    @After fun tearDown() = server.shutdown()

    private fun <T> DataResult<T>.value(): T = (this as DataResult.Success).value

    @Test fun `tineye query hash becomes the results page`() = runTest {
        server.enqueue(MockResponse().setBody("""{"page": 1, "query_hash": "abc123", "num_matches": 0}"""))
        val url = repo.directSearchUrl(DirectUploadEngine.TINEYE, image, ".jpg").value()
        assertTrue(url, url.endsWith("/tineye/search/abc123"))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("name=\"image\""))
        assertTrue(body, body.contains("Content-Type: image/jpeg"))
    }

    @Test fun `tineye answering a page instead of json is a failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>results</html>"))
        assertTrue(repo.directSearchUrl(DirectUploadEngine.TINEYE, image, ".jpg") is DataResult.Failure)
    }

    @Test fun `yandex gets the raw bytes and its cbir id opens results`() = runTest {
        server.enqueue(MockResponse().setBody("""{"cbir_id":"123/abc","namespace":"images-cbir","sizes":{}}"""))
        val url = repo.directSearchUrl(DirectUploadEngine.YANDEX, image, ".jpg").value()
        assertTrue(url, url.endsWith("/yandex/results?rpt=imageview&cbir_id=123%2Fabc"))
        val recorded = server.takeRequest()
        assertEquals("image/jpeg", recorded.getHeader("Content-Type"))
        assertEquals(3, recorded.bodySize)
    }

    @Test fun `garbage from yandex is a failure, not a crash`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        assertTrue(repo.directSearchUrl(DirectUploadEngine.YANDEX, image, ".jpg") is DataResult.Failure)
    }

    @Test fun `litterbox takes the file for an hour`() = runTest {
        server.enqueue(MockResponse().setBody("https://litter.catbox.moe/abc.jpg"))
        val hosted = repo.hostTemporarily(image, ".jpg").value()
        assertEquals("https://litter.catbox.moe/abc.jpg", hosted.url)
        assertEquals(TemporaryHost.LITTERBOX, hosted.host)
        val recorded = server.takeRequest()
        // No 4chan baggage: the shared client's cookie jar and cache headers stay off this host.
        assertEquals(null, recorded.getHeader("Cookie"))
        val body = recorded.body.readUtf8()
        assertTrue(body, body.contains("name=\"reqtype\""))
        assertTrue(body, body.contains("fileupload"))
        assertTrue(body, body.contains("name=\"time\""))
        assertTrue(body, body.contains("1h"))
        assertTrue(body, body.contains("name=\"fileToUpload\""))
    }

    @Test fun `a failed litterbox upload falls through to 0x0`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        server.enqueue(MockResponse().setBody("https://0x0.st/abcd.jpg"))
        val hosted = repo.hostTemporarily(image, ".jpg").value()
        assertEquals("https://0x0.st/abcd.jpg", hosted.url)
        assertEquals(TemporaryHost.ZERO_X_ZERO, hosted.host)
        server.takeRequest()
        val fallback = server.takeRequest()
        assertTrue(fallback.getHeader("User-Agent").orEmpty().startsWith("Yotsuba/"))
        assertEquals("24", fallback.getHeader("X-Expires"))
        assertTrue(fallback.body.readUtf8().contains("name=\"file\""))
    }

    @Test fun `a host answering with something that is not a URL is a failure`() = runTest {
        server.enqueue(MockResponse().setBody("<html>error</html>"))
        server.enqueue(MockResponse().setBody("also not a url"))
        assertTrue(repo.hostTemporarily(image, ".jpg") is DataResult.Failure)
    }
}

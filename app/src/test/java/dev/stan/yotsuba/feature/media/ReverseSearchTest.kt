package dev.stan.yotsuba.feature.media

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseSearchTest {

    private val image = "https://i.4cdn.org/g/1700000000000.jpg"

    @Test fun `every engine carries the image as an encoded query value`() {
        val expected = "https%3A%2F%2Fi.4cdn.org%2Fg%2F1700000000000.jpg"
        ReverseSearchEngine.entries.forEach { engine ->
            val url = engine.searchUrl(image)
            assertTrue("$engine: $url", url.endsWith(expected))
            assertFalse("$engine leaks a raw URL: $url", url.removePrefix("https://").contains("://"))
        }
    }

    @Test fun `ampersands and question marks in the image URL do not break the query`() {
        val tricky = "https://example.org/a.jpg?x=1&y=2"
        val url = ReverseSearchEngine.YANDEX.searchUrl(tricky)
        assertEquals(
            "https://yandex.com/images/search?rpt=imageview&url=https%3A%2F%2Fexample.org%2Fa.jpg%3Fx%3D1%26y%3D2",
            url,
        )
    }

    @Test fun `each engine has its own host`() {
        assertEquals("https://lens.google.com/uploadbyurl?url=x", ReverseSearchEngine.GOOGLE_LENS.searchUrl("x"))
        assertEquals("https://saucenao.com/search.php?url=x", ReverseSearchEngine.SAUCENAO.searchUrl("x"))
        assertEquals("https://iqdb.org/?url=x", ReverseSearchEngine.IQDB.searchUrl("x"))
        assertEquals("https://tineye.com/search?url=x", ReverseSearchEngine.TINEYE.searchUrl("x"))
    }

    @Test fun `only http URLs count as remote`() {
        assertEquals(image, remoteImageUrl(image))
        assertEquals("http://i/a.jpg", remoteImageUrl("http://i/a.jpg"))
        assertNull(remoteImageUrl("file:///sdcard/Yotsuba/_local/1/a.jpg"))
        assertNull(remoteImageUrl(""))
        assertNull(remoteImageUrl(null))
    }

    @Test fun `a target says which routes are open`() {
        val both = ReverseSearchTarget(image, File("/a.jpg"), ".jpg")
        assertTrue(both.canUseEngines)
        assertTrue(both.canShare)
        val frame = ReverseSearchTarget(null, File("/frame.jpg"), ".jpg")
        assertFalse(frame.canUseEngines)
        assertTrue(frame.canShare)
        val unsaved = ReverseSearchTarget(image, null, ".png")
        assertTrue(unsaved.canUseEngines)
        assertFalse(unsaved.canShare)
    }

    @Test fun `a local-only file can go to every engine, and nothing can go nowhere`() {
        val frame = ReverseSearchTarget(null, File("/frame.jpg"), ".jpg")
        val online = ReverseSearchTarget(image, null, ".jpg")
        ReverseSearchEngine.entries.forEach { engine ->
            assertTrue("$engine local", frame.canUse(engine))
            assertTrue("$engine online", online.canUse(engine))
            assertFalse("$engine empty", ReverseSearchTarget(null, null, ".jpg").canUse(engine))
        }
    }

    @Test fun `only engines with a usable upload form claim a direct route`() {
        assertEquals(EngineUploadStyle.REDIRECT, ReverseSearchEngine.TINEYE.uploadStyle)
        assertEquals(EngineUploadStyle.JSON, ReverseSearchEngine.YANDEX.uploadStyle)
        assertEquals(EngineUploadStyle.NONE, ReverseSearchEngine.SAUCENAO.uploadStyle)
        assertEquals(EngineUploadStyle.NONE, ReverseSearchEngine.IQDB.uploadStyle)
        assertEquals(EngineUploadStyle.NONE, ReverseSearchEngine.GOOGLE_LENS.uploadStyle)
    }
}

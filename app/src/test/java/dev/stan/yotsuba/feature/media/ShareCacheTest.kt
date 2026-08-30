package dev.stan.yotsuba.feature.media

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric for the cache directory and a Bitmap to compress; nothing here touches a decoder. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ShareCacheTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `a frame is named after its video and its time`() {
        assertEquals("clip-1500ms.jpg", ShareCache.frameFileName(File("/v/clip.webm"), 1_500))
        assertEquals("a.b-0ms.jpg", ShareCache.frameFileName(File("a.b.mp4"), -20))
    }

    @Test fun `a frame lands in the share cache and older files are trimmed around it`() {
        val dir = ShareCache.dir(context)
        repeat(25) { i ->
            File(dir, "old$i.jpg").apply {
                writeText("x")
                setLastModified(1_000_000L + i * 1_000)
            }
        }
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val frame = ShareCache.writeJpeg(context, bitmap, "clip-10ms.jpg")!!
        val names = dir.list()!!.toSet()
        assertEquals("shared_media", frame.parentFile?.name)
        assertTrue(frame.length() > 0)
        assertEquals(ShareCache.LIMIT, names.size)
        assertTrue("clip-10ms.jpg" in names)
        assertTrue("old24.jpg" in names)
        assertFalse("old0.jpg" in names)
    }

    @Test fun `trim keeps the newest files and never the one being handed out`() {
        val dir = File(context.cacheDir, "trim-test").apply { mkdirs() }
        val files = (0 until 30).map { i ->
            File(dir, "$i.jpg").apply {
                writeText("x")
                setLastModified(1_000_000L + i * 1_000)
            }
        }
        // The oldest file is the one being shared: it must survive.
        ShareCache.trim(dir, keep = files[0])
        val left = dir.list()!!.toSet()
        assertEquals(ShareCache.LIMIT, left.size)
        assertTrue("0.jpg" in left)
        assertTrue("29.jpg" in left)
        assertFalse("1.jpg" in left)
    }
}

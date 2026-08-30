package dev.stan.yotsuba.dedup

import android.graphics.Bitmap
import android.graphics.Color
import dev.stan.yotsuba.core.dedup.DHash
import dev.stan.yotsuba.core.dedup.Md5
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DHashTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Left-to-right brightness ramp with a diagonal so the flip is not its own mirror. */
    private fun gradient(w: Int = 128, h: Int = 96): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) for (x in 0 until w) {
            val v = ((x * 255) / w + (if (x > y) 40 else 0)).coerceIn(0, 255)
            bmp.setPixel(x, y, Color.rgb(v, v, v))
        }
        return bmp
    }

    /** Mirrored pixel by pixel: Robolectric's legacy bitmaps ignore a Matrix. */
    private fun flipped(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until src.height) for (x in 0 until src.width) {
            out.setPixel(src.width - 1 - x, y, src.getPixel(x, y))
        }
        return out
    }

    @Test fun `identical bitmaps hash the same`() {
        assertEquals(DHash.of(gradient()), DHash.of(gradient()))
    }

    @Test fun `a flipped copy hashes differently`() {
        val g = gradient()
        val a = DHash.of(g)
        val b = DHash.of(flipped(g))
        assertNotEquals(a, b)
        assertTrue(DHash.distance(a, b) > 6)
    }

    @Test fun `distance is the popcount of the xor`() {
        assertEquals(0, DHash.distance(0L, 0L))
        assertEquals(3, DHash.distance(0b0111L, 0L))
        assertEquals(64, DHash.distance(-1L, 0L))
    }

    @Test fun `sample size brings the longer edge down to about 64 px`() {
        assertEquals(1, DHash.sampleSize(64))
        assertEquals(1, DHash.sampleSize(100))
        assertEquals(2, DHash.sampleSize(128))
        assertEquals(16, DHash.sampleSize(1024))
        assertEquals(64, DHash.sampleSize(4096))
    }

    @Test fun `hashing a file reports the true dimensions`() {
        val file = File(tmp.root, "g.png")
        file.outputStream().use { gradient(200, 100).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val hash = DHash.of(file)!!
        assertEquals(200, hash.width)
        assertEquals(100, hash.height)
    }

    @Test fun `md5 is base64 of the raw digest`() {
        val file = File(tmp.root, "a.txt").apply { writeText("abc") }
        // MD5("abc") = 900150983cd24fb0d6963f7d28e17f72
        assertEquals("kAFQmDzST7DWlj99KOF/cg==", Md5.of(file))
    }
}

package dev.stan.yotsuba.media

import dev.stan.yotsuba.core.media.AnimatedWebp
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimatedWebpTest {

    @Test fun `frames are sampled every 100 ms from zero and capped at sixty`() {
        assertEquals(listOf(0L), AnimatedWebp.frameTimes(0))
        assertEquals(listOf(0L), AnimatedWebp.frameTimes(99))
        assertEquals(listOf(0L, 100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L), AnimatedWebp.frameTimes(950))
        val long = AnimatedWebp.frameTimes(30_000)
        assertEquals(60, long.size)
        assertEquals(5_900L, long.last())
    }

    @Test fun `mux wraps each still's image chunk in an ANMF and sizes the RIFF header`() {
        // A minimal still: RIFF header, then one three-byte VP8 chunk, which RIFF pads to four.
        val still = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray()); write(byteArrayOf(15, 0, 0, 0)); write("WEBP".toByteArray())
            write("VP8 ".toByteArray()); write(byteArrayOf(3, 0, 0, 0)); write(byteArrayOf(1, 2, 3, 0))
        }.toByteArray()
        val out = ByteArrayOutputStream()
        AnimatedWebp.mux(listOf(AnimatedWebp.Frame(still, 512, 288), AnimatedWebp.Frame(still, 512, 288)), 100, out)
        val bytes = out.toByteArray()

        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals(bytes.size - 8, bytes[4].toInt() and 0xff or ((bytes[5].toInt() and 0xff) shl 8))
        assertEquals("WEBPVP8X", String(bytes, 8, 8))
        // VP8X: animation flag, then canvas width-1 and height-1 as 24-bit little-endian.
        assertEquals(0x02, bytes[20].toInt())
        assertArrayEquals(byteArrayOf(0xff.toByte(), 1, 0, 0x1f, 1, 0), bytes.copyOfRange(24, 30))
        assertEquals("ANIM", String(bytes, 30, 4))
        val anmf = 30 + 8 + 6
        assertEquals("ANMF", String(bytes, anmf, 4))
        assertEquals(16 + 12, bytes[anmf + 4].toInt())
        // Duration 100 ms, no-blend flag, then the still's VP8 chunk byte for byte.
        assertArrayEquals(byteArrayOf(100, 0, 0, 0x02), bytes.copyOfRange(anmf + 8 + 12, anmf + 8 + 16))
        assertArrayEquals(still.copyOfRange(12, still.size), bytes.copyOfRange(anmf + 24, anmf + 36))
        assertEquals("ANMF", String(bytes, anmf + 36, 4))
        assertEquals(anmf + 72, bytes.size)
    }
}

package dev.stan.yotsuba.core.media

import android.graphics.Bitmap
import android.os.Build
import dev.stan.yotsuba.core.vault.VideoStills
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Turns the opening seconds of a video into an animated WebP, the sticker format the
 * chat apps take. The platform encodes single WebP stills but has no animated writer at
 * any API level, so the frames come out of [Bitmap.compress] one by one and [mux] wraps
 * them in the animation container by hand: a VP8X header, an ANIM chunk, one ANMF per
 * frame carrying the still's own bitstream unchanged.
 */
object AnimatedWebp {
    /** A sticker is a loop, not a clip: [MAX_FRAMES] at [FRAME_MS] is the first six seconds. */
    const val MAX_FRAMES = 60
    const val FRAME_MS = 100L
    const val MAX_EDGE = 512
    private const val QUALITY = 80

    /** The timestamps to pull for a video of [durationMs]: every [FRAME_MS] from zero, capped at [MAX_FRAMES]. */
    fun frameTimes(durationMs: Long): List<Long> {
        val count = (durationMs / FRAME_MS).toInt().coerceIn(1, MAX_FRAMES)
        return List(count) { it * FRAME_MS }
    }

    /** One frame as a still WebP, the bytes [Bitmap.compress] wrote, with its size. */
    class Frame(val webp: ByteArray, val width: Int, val height: Int)

    /**
     * Decodes [video] at [frameTimes], shrinks each frame to [MAX_EDGE] and writes the
     * animation to [out]. [onProgress] is called from the IO thread with the share of
     * frames done. False when the video will not open or yields no frame; cancellation
     * removes whatever was written.
     */
    suspend fun fromVideo(video: File, out: File, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val source = VideoStills.FrameSource.open(video) ?: return@withContext false
        try {
            val times = frameTimes(source.durationMs)
            val frames = ArrayList<Frame>(times.size)
            for ((i, time) in times.withIndex()) {
                ensureActive()
                source.frameAt(time)?.let { frames += encode(it) }
                onProgress((i + 1).toFloat() / times.size)
            }
            if (frames.isEmpty()) return@withContext false
            out.outputStream().buffered().use { mux(frames, FRAME_MS.toInt(), it) }
            true
        } catch (e: Exception) {
            out.delete()
            if (e is CancellationException) throw e
            false
        } finally {
            source.close()
        }
    }

    private fun encode(frame: Bitmap): Frame {
        val scaled = VideoStills.shrink(frame, MAX_EDGE)
        val bytes = ByteArrayOutputStream().also { scaled.compress(lossy, QUALITY, it) }.toByteArray()
        val result = Frame(bytes, scaled.width, scaled.height)
        if (scaled !== frame) scaled.recycle()
        frame.recycle()
        return result
    }

    @Suppress("DEPRECATION")
    private val lossy = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    /** Writes [frames], each shown for [delayMs], as one looping animated WebP to [out]. */
    fun mux(frames: List<Frame>, delayMs: Int, out: OutputStream) {
        val payloads = frames.map { imageChunks(it.webp) }
        val anmfs = frames.zip(payloads) { frame, payload ->
            chunk("ANMF") {
                le(0, 3); le(0, 3) // Offset: every frame fills the canvas from the corner.
                le(frame.width - 1, 3); le(frame.height - 1, 3)
                le(delayMs, 3)
                write(NO_BLEND)
                write(payload.chunks)
            }
        }
        val vp8x = chunk("VP8X") {
            write(ANIMATED or if (payloads.any { it.hasAlpha }) ALPHA else 0)
            le(0, 3)
            le(frames.maxOf { it.width } - 1, 3); le(frames.maxOf { it.height } - 1, 3)
        }
        val anim = chunk("ANIM") { le(0, 4); le(0, 2) } // Transparent background, loop forever.
        out.write(RIFF)
        out.le(4 + vp8x.size + anim.size + anmfs.sumOf { it.size }, 4)
        out.write(WEBP)
        out.write(vp8x); out.write(anim); anmfs.forEach { out.write(it) }
    }

    private class Payload(val chunks: ByteArray, val hasAlpha: Boolean)

    /**
     * The image chunks of a still WebP, ALPH then VP8 or VP8L, exactly as the still holds
     * them, which is what an ANMF frame carries. Metadata chunks are dropped.
     */
    private fun imageChunks(webp: ByteArray): Payload {
        if (webp.size < 12 || !webp.copyOfRange(0, 4).contentEquals(RIFF) || !webp.copyOfRange(8, 12).contentEquals(WEBP)) {
            throw IOException("not a WebP")
        }
        val out = ByteArrayOutputStream()
        var hasAlpha = false
        var at = 12
        while (at + 8 <= webp.size) {
            val fourcc = String(webp, at, 4, Charsets.US_ASCII)
            val size = (webp[at + 4].toInt() and 0xff) or ((webp[at + 5].toInt() and 0xff) shl 8) or
                ((webp[at + 6].toInt() and 0xff) shl 16) or ((webp[at + 7].toInt() and 0xff) shl 24)
            val end = at + 8 + size + (size and 1)
            if (size < 0 || end > webp.size) throw IOException("truncated WebP chunk $fourcc")
            if (fourcc == "ALPH") hasAlpha = true
            if (fourcc == "ALPH" || fourcc == "VP8 " || fourcc == "VP8L") out.write(webp, at, end - at)
            at = end
        }
        if (out.size() == 0) throw IOException("WebP without an image chunk")
        return Payload(out.toByteArray(), hasAlpha)
    }

    /** A RIFF chunk: fourcc, little-endian size, the body, a pad byte when the body is odd. */
    private fun chunk(fourcc: String, body: ByteArrayOutputStream.() -> Unit): ByteArray {
        val data = ByteArrayOutputStream().apply(body).toByteArray()
        return ByteArrayOutputStream(8 + data.size + 1).apply {
            write(fourcc.toByteArray(Charsets.US_ASCII))
            le(data.size, 4)
            write(data)
            if (data.size and 1 == 1) write(0)
        }.toByteArray()
    }

    private fun OutputStream.le(value: Int, bytes: Int) {
        repeat(bytes) { write((value ushr (8 * it)) and 0xff) }
    }

    private val RIFF = "RIFF".toByteArray(Charsets.US_ASCII)
    private val WEBP = "WEBP".toByteArray(Charsets.US_ASCII)
    private const val ANIMATED = 0x02
    private const val ALPHA = 0x10
    /** ANMF flags: draw over the previous frame without alpha blending, keep it afterwards. */
    private const val NO_BLEND = 0x02
}

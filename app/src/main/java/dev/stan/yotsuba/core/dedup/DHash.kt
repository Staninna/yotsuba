package dev.stan.yotsuba.core.dedup

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** What the hasher learned about one image: its dHash and its true dimensions. */
data class ImageHash(val dhash: Long, val width: Int, val height: Int)

/**
 * 64-bit difference hash: shrink to 9x8 greyscale, set a bit where a pixel is brighter
 * than its right-hand neighbour. Survives rescaling and recompression; a flip changes it.
 */
object DHash {
    private const val TARGET_EDGE = 64
    // One bit per horizontal neighbour pair: ROWS * (COLS - 1) = 64, the width of the hash.
    private const val COLS = 9
    private const val ROWS = 8

    /** Null when the file isn't a decodable image. */
    fun of(file: File): ImageHash? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(maxOf(w, h))
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.path, opts) ?: return null
        return try {
            ImageHash(of(bitmap), w, h)
        } finally {
            bitmap.recycle()
        }
    }

    /** Largest power of two that keeps the longer edge at or above [TARGET_EDGE]. */
    internal fun sampleSize(longerEdge: Int): Int {
        var sample = 1
        while (longerEdge / (sample * 2) >= TARGET_EDGE) sample *= 2
        return sample
    }

    /** Box-averages the bitmap down to 9x8 grey itself, so the result does not depend on the platform scaler. */
    fun of(bitmap: Bitmap): Long {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val cell = LongArray(COLS * ROWS)
        val count = IntArray(COLS * ROWS)
        for (y in 0 until h) {
            val cy = (y.toLong() * ROWS / h).toInt()
            for (x in 0 until w) {
                val cx = (x.toLong() * COLS / w).toInt()
                val i = cy * COLS + cx
                cell[i] += grey(pixels[y * w + x])
                count[i]++
            }
        }
        var hash = 0L
        for (y in 0 until ROWS) {
            for (x in 0 until COLS - 1) {
                val left = cell[y * COLS + x] * (count[y * COLS + x + 1].coerceAtLeast(1))
                val right = cell[y * COLS + x + 1] * (count[y * COLS + x].coerceAtLeast(1))
                hash = (hash shl 1) or (if (left > right) 1L else 0L)
            }
        }
        return hash
    }

    private fun grey(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}

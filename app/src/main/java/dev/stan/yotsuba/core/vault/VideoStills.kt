package dev.stan.yotsuba.core.vault

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File

/**
 * First-frame stills for saved videos, kept next to the video under
 * [VaultPaths.THUMBS_DIR_NAME]. The vault is `.nomedia`, so nothing else indexes them,
 * and the still is what the grid shows instead of a re-fetched CDN thumbnail.
 */
object VideoStills {
    data class Still(val file: File, val durationMs: Long?)

    private const val MAX_EDGE = 512

    /** Where [video]'s still lives, whether or not it has been captured. */
    fun stillFor(video: File): File =
        File(File(video.parentFile, VaultPaths.THUMBS_DIR_NAME), video.name + ".jpg")

    /**
     * Captures the still if it is missing and reads the duration. Null when the file cannot
     * be decoded, which for a corrupt or exotic webm is not worth failing a save over.
     */
    fun capture(video: File): Still? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(video.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val still = stillFor(video)
            if (!still.isFile) {
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return Still(still.takeIf { it.isFile } ?: return null, duration)
                still.parentFile?.mkdirs()
                val scaled = shrink(frame)
                val tmp = File(still.parentFile, still.name + ".tmp")
                tmp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                if (!tmp.renameTo(still)) {
                    tmp.delete()
                    return null
                }
            }
            Still(still, duration)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun shrink(frame: Bitmap): Bitmap {
        val edge = maxOf(frame.width, frame.height)
        if (edge <= MAX_EDGE) return frame
        val scale = MAX_EDGE.toFloat() / edge
        return Bitmap.createScaledBitmap(
            frame, (frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1), true,
        )
    }
}

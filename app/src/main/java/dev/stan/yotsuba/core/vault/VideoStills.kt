package dev.stan.yotsuba.core.vault

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import dev.stan.yotsuba.domain.model.VaultPaths
import dev.stan.yotsuba.domain.model.isVideoExt
import java.io.Closeable
import java.io.File

/**
 * First-frame stills for saved videos, kept next to the video under
 * [VaultPaths.THUMBS_DIR_NAME]. The vault is `.nomedia`, so nothing else indexes them,
 * and the still is what the grid shows instead of a re-fetched CDN thumbnail.
 */
object VideoStills {
    data class Still(
        val file: File,
        val durationMs: Long?,
        /** Whether the container reports an audio track; null when the retriever would not say. */
        val hasAudio: Boolean?,
    )

    private const val MAX_EDGE = 512

    /**
     * One open decoder over a video, for pulling frames at chosen times without re-opening
     * the file each time. Not thread-safe: call it from one thread and [close] it when
     * done, or the native decoder stays allocated until the finaliser gets round to it.
     */
    class FrameSource private constructor(
        private val retriever: MediaMetadataRetriever,
        /** Zero when the container does not say. */
        val durationMs: Long,
        val hasAudio: Boolean,
    ) : Closeable {
        /**
         * The frame nearest [timeMs], decoded exactly rather than snapped to a keyframe,
         * so what the picker shows is what gets searched. Null when the decoder fails.
         */
        fun frameAt(timeMs: Long): Bitmap? = runCatching {
            retriever.getFrameAtTime(timeMs.coerceAtLeast(0) * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
        }.getOrNull()

        /** The first sync frame, which is what the vault still shows. */
        fun firstFrame(): Bitmap? = runCatching {
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }.getOrNull()

        override fun close() {
            runCatching { retriever.release() }
        }

        companion object {
            /** Null when [video] cannot be opened at all. */
            fun open(video: File): FrameSource? {
                val retriever = MediaMetadataRetriever()
                return try {
                    retriever.setDataSource(video.absolutePath)
                    FrameSource(
                        retriever,
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull() ?: 0L,
                        hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
                    )
                } catch (e: Exception) {
                    runCatching { retriever.release() }
                    null
                }
            }
        }
    }

    /** Where [video]'s still lives, whether or not it has been captured. */
    fun stillFor(video: File): File =
        File(File(video.parentFile, VaultPaths.THUMBS_DIR_NAME), video.name + ".jpg")

    /** [capture] for a video; nothing for anything else. */
    fun captureIfVideo(file: File): Still? =
        if (isVideoExt(VaultPaths.extensionOf(file.name))) capture(file) else null

    /**
     * Captures the still if it is missing and reads the duration and whether there is sound. Null when the file cannot
     * be decoded, which for a corrupt or exotic webm is not worth failing a save over.
     */
    fun capture(video: File): Still? {
        val source = FrameSource.open(video) ?: return null
        return try {
            val duration = source.durationMs.takeIf { it > 0 }
            val hasAudio = source.hasAudio
            val still = stillFor(video)
            if (!still.isFile) {
                val frame = source.firstFrame() ?: return null
                still.parentFile?.mkdirs()
                val scaled = shrink(frame)
                val tmp = File(still.parentFile, still.name + ".tmp")
                tmp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                // Full-resolution frames, one per video in a rescan loop: hand the memory back now.
                if (scaled !== frame) scaled.recycle()
                frame.recycle()
                if (!tmp.renameTo(still)) {
                    tmp.delete()
                    return null
                }
            }
            Still(still, duration, hasAudio)
        } catch (e: Exception) {
            null
        } finally {
            source.close()
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

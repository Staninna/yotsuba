package dev.stan.yotsuba.domain.model

import dev.stan.yotsuba.core.vault.VaultPaths

/**
 * Where a vault file is filed: one thread directory under one board directory. The board
 * is always a real directory name, so the unsorted bucket and locally imported threads are
 * ordinary locations under their reserved boards rather than special cases.
 *
 * Board plus thread number is the whole identity. The subject is display data that drifts
 * between rows and lives on the [VaultEntry] instead.
 */
data class VaultLocation(val board: String, val threadNo: Long) {
    val isUnsorted: Boolean get() = board == VaultPaths.UNSORTED_DIR_NAME
    val isLocal: Boolean get() = board == VaultPaths.LOCAL_BOARD_NAME

    /** Saved from a live 4chan thread, so it has a conversation and an upstream. */
    val isRemote: Boolean get() = !isUnsorted && !isLocal

    companion object {
        /** Migration leftovers with no thread attribution. */
        val Unsorted = VaultLocation(VaultPaths.UNSORTED_DIR_NAME, 0L)
    }
}

/** One media file saved into the on-disk vault, keyed by its full CDN URL. */
data class VaultEntry(
    val url: String,
    val location: VaultLocation,
    /** Thread subject as recorded on this row; null for untitled threads and unsorted files. */
    val subject: String?,
    val postNo: Long?,
    /** File name on disk (inside its thread directory). */
    val displayName: String,
    val absolutePath: String,
    val ext: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    /** Remote thumbnail, used for video previews in the explorer grid. */
    val thumbnailUrl: String?,
    val savedAt: Long,
    /** First-frame still on disk; the grid prefers it over [thumbnailUrl]. */
    val localThumbnailPath: String? = null,
    /** Video length, millis; null for images and for videos not yet probed. */
    val durationMs: Long? = null,
) {
    val isVideo: Boolean get() = ext == ".webm" || ext == ".mp4"
}

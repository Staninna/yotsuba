package dev.stan.yotsuba.domain.model

/** Where a vault file is filed: under a thread, or in the unsorted bucket. */
sealed interface VaultLocation {
    data class Thread(val board: String, val threadNo: Long, val subject: String?) : VaultLocation
    data object Unsorted : VaultLocation
}

/** One media file saved into the on-disk vault, keyed by its full CDN URL. */
data class VaultEntry(
    val url: String,
    val location: VaultLocation,
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
) {
    val isVideo: Boolean get() = ext == ".webm" || ext == ".mp4"
}

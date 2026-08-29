package dev.stan.yotsuba.domain.model

/**
 * An attachment on a post. [Present] carries the real, non-fabricated fields;
 * a file 4chan removed is [Deleted] and has no URLs or dimensions to offer.
 */
sealed interface PostMedia {
    data class Present(val item: MediaItem) : PostMedia

    /** The file was deleted server-side; only its display name survives. */
    data class Deleted(val displayName: String) : PostMedia
}

data class MediaItem(
    val postNo: Long,
    val filename: String,
    val ext: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val thumbnailUrl: String,
    val fullUrl: String,
    val spoiler: Boolean,
    /** External audio for a "sound post", played alongside the visual. Null for most files. */
    val soundUrl: String? = null,
) {
    val hasSound: Boolean get() = soundUrl != null

    val isVideo: Boolean get() = ext == ".webm" || ext == ".mp4"
    val isAnimated: Boolean get() = isVideo || ext == ".gif"
    val displayName: String get() = "$filename$ext"
}

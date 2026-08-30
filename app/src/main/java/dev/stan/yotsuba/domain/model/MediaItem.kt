package dev.stan.yotsuba.domain.model

import dev.stan.yotsuba.core.media.isVideoExt

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
    /** 4chan's MD5 of the file, base64 of the raw digest. Null when the source didn't say. */
    val md5: String? = null,
) {
    val isVideo: Boolean get() = isVideoExt(ext)
    val isAnimated: Boolean get() = isVideo || ext == ".gif"
    val displayName: String get() = "$filename$ext"
}

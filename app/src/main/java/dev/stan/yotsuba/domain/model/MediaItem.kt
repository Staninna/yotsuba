package dev.stan.yotsuba.domain.model

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
    val deleted: Boolean,
) {
    val isVideo: Boolean get() = ext == ".webm" || ext == ".mp4"
    val isAnimated: Boolean get() = isVideo || ext == ".gif"
    val displayName: String get() = "$filename$ext"
}

package dev.stan.yotsuba.core.media

/** The MIME type for a 4chan file extension (with its dot); anything unfamiliar is a blob. */
fun mimeOf(ext: String): String = when (ext.lowercase()) {
    ".jpg", ".jpeg" -> "image/jpeg"
    ".png" -> "image/png"
    ".gif" -> "image/gif"
    ".webp" -> "image/webp"
    ".webm" -> "video/webm"
    ".mp4" -> "video/mp4"
    else -> "application/octet-stream"
}

fun isVideoExt(ext: String): Boolean = mimeOf(ext).startsWith("video/")

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

/** The extensions 4chan serves as video; the DAO binds this set so its SQL never repeats it. */
val VIDEO_EXTS: Set<String> = setOf(".webm", ".mp4")

fun isVideoExt(ext: String): Boolean = ext.lowercase() in VIDEO_EXTS

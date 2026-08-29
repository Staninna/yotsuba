package dev.stan.yotsuba.core.vault

/**
 * Pure-Kotlin naming rules for the on-disk media vault:
 *
 * ```
 * Yotsuba/
 * ├── .nomedia
 * ├── _unsorted/            ← migration leftovers without thread attribution
 * └── <board>/<threadNo> - <subject slug>/<postNo>_<original filename><ext>
 * ```
 *
 * Every thread directory carries a [meta file][META_FILE_NAME] describing each saved file,
 * and optionally a [posts file][POSTS_FILE_NAME] holding the surrounding conversation as
 * text. They are separate because meta.json is rewritten on every single save, and folding
 * a few hundred posts into that hot path would rewrite the lot each time.
 */
object VaultPaths {
    const val ROOT_DIR_NAME = "Yotsuba"
    const val NOMEDIA_FILE_NAME = ".nomedia"
    const val META_FILE_NAME = "meta.json"
    const val POSTS_FILE_NAME = "posts.json"
    const val UNSORTED_DIR_NAME = "_unsorted"

    /**
     * Board segment for threads the user assembled themselves from local files.
     * Leading underscore matches [UNSORTED_DIR_NAME] and cannot collide with a real
     * 4chan board code.
     */
    const val LOCAL_BOARD_NAME = "_local"

    /** `".jpg"` from `"holiday.JPG"`; empty when the name carries no extension. */
    fun extensionOf(displayName: String): String {
        val dot = displayName.lastIndexOf('.')
        if (dot <= 0 || dot == displayName.lastIndex) return ""
        return displayName.substring(dot).lowercase()
    }

    /** FAT/exFAT-illegal characters plus control chars; replaced, never dropped, to keep names readable. */
    private val ILLEGAL = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

    private const val MAX_SEGMENT_LENGTH = 80

    /** Makes [raw] a safe single path segment; never empty (falls back to "_"). */
    fun sanitizeSegment(raw: String): String {
        val cleaned = ILLEGAL.replace(raw, "_")
            .trim()
            .trimEnd('.')
            .take(MAX_SEGMENT_LENGTH)
            .trim()
        return cleaned.ifEmpty { "_" }
    }

    /** FAT/exFAT-illegal characters plus control chars; replaced, never dropped, to keep names readable. */
    private val ILLEGAL = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

    private const val MAX_SEGMENT_LENGTH = 80

    /** Makes [raw] a safe single path segment; never empty (falls back to "_"). */
    fun sanitizeSegment(raw: String): String {
        val cleaned = ILLEGAL.replace(raw, "_")
            .trim()
            .trimEnd('.')
            .take(MAX_SEGMENT_LENGTH)
            .trim()
        return cleaned.ifEmpty { "_" }
    }

    /**
     * Directory name for a thread: `"<threadNo> - <slug>"` where the slug comes from the
     * subject, else the OP excerpt, else nothing (`"<threadNo>"` alone).
     */
    fun threadDirName(threadNo: Long, subject: String?, opExcerpt: String? = null): String {
        val slugSource = subject?.takeIf { it.isNotBlank() } ?: opExcerpt?.takeIf { it.isNotBlank() }
        val slug = slugSource?.let { sanitizeSegment(it.take(60)) }?.takeIf { it != "_" }
        return if (slug != null) "$threadNo - $slug" else "$threadNo"
    }

    /** File name for a saved post attachment: `"<postNo>_<original filename><ext>"`. */
    fun fileName(postNo: Long, originalFilename: String, ext: String): String =
        sanitizeSegment("${postNo}_$originalFilename") + ext

    /**
     * Resolves a collision by inserting ` (n)` before the extension:
     * `123_a.jpg` → `123_a (1).jpg`.
     */
    fun dedupedFileName(name: String, attempt: Int): String {
        if (attempt <= 0) return name
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) "$name ($attempt)"
        else "${name.substring(0, dot)} ($attempt)${name.substring(dot)}"
    }

    /** Parses `https://i.4cdn.org/<board>/<tim><ext>` → (board, tim, ext), or null. */
    fun parseMediaUrl(url: String): ParsedMediaUrl? {
        val m = MEDIA_URL.matchEntire(url) ?: return null
        return ParsedMediaUrl(
            board = m.groupValues[1],
            tim = m.groupValues[2].toLongOrNull() ?: return null,
            ext = m.groupValues[3],
        )
    }

    private val MEDIA_URL = Regex("""https?://i\.4cdn\.org/(\w+)/(\d+)(\.\w+)""")

    data class ParsedMediaUrl(val board: String, val tim: Long, val ext: String)
}

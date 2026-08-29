package dev.stan.yotsuba.domain.model

/** How [DuplicateGroup]s are formed: byte-identical files, or images that look alike. */
enum class DedupMode { EXACT, SIMILAR }

/** One vault file as the duplicate finder sees it. */
data class DuplicateEntry(
    val url: String,
    val absolutePath: String,
    val displayName: String,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val savedAt: Long,
    val subject: String?,
    val isVideo: Boolean,
    /** Local still for a video, so the sheet has something to show. */
    val thumbnailPath: String? = null,
) {
    val pixelSize: Long get() = (width ?: 0).toLong() * (height ?: 0).toLong()
}

/**
 * Files that are, or look like, the same picture. [keeperUrl] is the suggested survivor:
 * most pixels, then most bytes, then the oldest save.
 */
data class DuplicateGroup(
    val entries: List<DuplicateEntry>,
    val keeperUrl: String,
) {
    val redundant: List<DuplicateEntry> get() = entries.filter { it.url != keeperUrl }
    val redundantBytes: Long get() = redundant.sumOf { it.sizeBytes }
}

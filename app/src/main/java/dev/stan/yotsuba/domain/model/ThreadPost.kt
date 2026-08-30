package dev.stan.yotsuba.domain.model

import dev.stan.yotsuba.domain.model.PostText

data class ThreadPost(
    val board: String,
    val no: Long,
    val isOp: Boolean,
    val name: String,
    val tripcode: String?,
    val capcode: String?,
    val posterId: String?,
    val countryCode: String?,
    val countryName: String?,
    val timeSeconds: Long,
    val subject: String?,
    val body: PostText,
    val media: PostMedia?,
    /** Post numbers this post quotes, for the reverse backlink index (D11). */
    val quotedPostNos: List<Long>,
) {
    /** The attachment's real fields, or null when there is no file or it was deleted. */
    val presentMedia: MediaItem? get() = (media as? PostMedia.Present)?.item
}

data class ThreadDetails(
    val board: String,
    val threadNo: Long,
    val posts: List<ThreadPost>,
    val archived: Boolean,
    /** Closed by a moderator: readable, but no new posts will come, so polling stops. */
    val closed: Boolean,
    /** postNo -> posts that quote it, computed once per thread (D11). */
    val backlinks: Map<Long, List<Long>>,
    val sticky: Boolean = false,
    /** Set when the posts came from a third-party archive rather than 4chan. */
    val archive: ArchiveSource? = null,
    /** Set when the posts were rebuilt from the vault sidecar because nothing else answered. */
    val offlineCopy: Boolean = false,
)

/** Third-party archives, in lookup order for boards more than one of them carries. */
enum class ArchiveSource(val label: String) {
    DESU("desuarchive.org"),
    B4K("arch.b4k.co"),
    /** Listed for the board table; has no JSON API, so nothing reads from it yet. */
    WAROSU("warosu.org"),
}

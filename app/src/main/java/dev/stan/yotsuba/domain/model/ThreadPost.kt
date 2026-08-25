package dev.stan.yotsuba.domain.model

import dev.stan.yotsuba.core.text.PostText

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
    val closed: Boolean,
    /** postNo -> posts that quote it, computed once per thread (D11). */
    val backlinks: Map<Long, List<Long>>,
)

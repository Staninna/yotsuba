package dev.stan.yotsuba.domain.model

import dev.stan.yotsuba.domain.model.PostText

data class CatalogThread(
    val board: String,
    val no: Long,
    val subject: String?,
    val excerpt: PostText,
    val thumbnailUrl: String?,
    val replyCount: Int,
    val imageCount: Int,
    val lastModified: Long,
    val sticky: Boolean,
    val closed: Boolean,
    /** Post numbers of the newest replies the catalog carries (a handful at most), oldest first. */
    val lastReplyNos: List<Long> = emptyList(),
) {
    val displayTitle: String
        get() = threadDisplayTitle(subject, excerpt.plainText, fallback = "#$no")
}

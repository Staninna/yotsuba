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
    /** When the OP was posted, epoch seconds. */
    val createdAt: Long = 0,
    /** Other threads on this board that the OP or the newest replies quote. */
    val quotedThreadNos: Set<Long> = emptySet(),
) {
    val displayTitle: String
        get() = threadDisplayTitle(subject, excerpt.plainText, fallback = "#$no")
}

/** Catalog order. [BUMP_ORDER] is the API's own order; the rest sort descending on one number. */
enum class CatalogSort { BUMP_ORDER, CREATION_TIME, REPLY_COUNT, IMAGE_COUNT, REPLIES_PER_HOUR }

package dev.stan.yotsuba.domain.model

import dev.stan.yotsuba.core.text.PostText

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
) {
    val displayTitle: String
        get() = threadDisplayTitle(subject, excerpt.plainText, fallback = "#$no")
}

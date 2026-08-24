package dev.stan.yotsuba.domain.model

data class HistoryEntry(
    val board: String,
    val threadNo: Long,
    val subject: String?,
    val opExcerpt: String,
    val thumbnailUrl: String?,
    val viewedAt: Long,
    val lastScrollPostNo: Long?,
)

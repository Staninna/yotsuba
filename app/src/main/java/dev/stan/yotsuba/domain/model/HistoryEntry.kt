package dev.stan.yotsuba.domain.model

data class HistoryEntry(
    val board: String,
    val threadNo: Long,
    val subject: String?,
    val opExcerpt: String,
    val thumbnailUrl: String?,
    val viewedAt: Long,
    val lastScrollPostNo: Long?,
) {
    val displayTitle: String
        get() = subject ?: opExcerpt.take(60).ifBlank { "/$board/$threadNo" }
}

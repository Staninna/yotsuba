package dev.stan.yotsuba.domain.model

enum class BookmarkState { ALIVE, DEAD, UNKNOWN }

data class Bookmark(
    val board: String,
    val threadNo: Long,
    val subject: String?,
    val opExcerpt: String,
    val thumbnailUrl: String?,
    val replyCount: Int,
    val imageCount: Int,
    val bookmarkedAt: Long,
    val lastCheckedAt: Long?,
    val lastSeenPostNo: Long?,
    val state: BookmarkState,
    /** Replies that arrived since the thread was last opened. */
    val newReplies: Int = 0,
    /** Posts after the saved reading position — everything not actually read yet. */
    val unreadCount: Int = 0,
)

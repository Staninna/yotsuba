package dev.stan.yotsuba.domain.model

enum class BookmarkState {
    ALIVE,

    /** The thread returned `archived == 1`: read-only but still fetchable. */
    ARCHIVED,

    /** Pruned: 404 from the API and gone from the catalog. Only the snapshot remains. */
    DEAD,
    UNKNOWN,
}

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
    /** Legacy last-visit marker; superseded by [readUpTo]. */
    @Deprecated("Use readUpTo") val lastSeenPostNo: Long?,
    val state: BookmarkState,
    @Deprecated("Derived: see unread") val newReplies: Int = 0,
    @Deprecated("Derived: see unread") val unreadCount: Int = 0,
    /** The one read mark: the newest post the user has had on screen. Null until first opened. */
    val readUpTo: Long? = null,
    /** Post numbers as of the last refresh (OP excluded). */
    val postNos: List<Long> = emptyList(),
    val pinned: Boolean = false,
    /** Catalog last_modified as of the last refresh; falls back to bookmarkedAt for sorting. */
    val lastActivityAt: Long? = null,
) {
    val displayTitle: String
        get() = subject ?: opExcerpt.take(60).ifBlank { "/$board/$threadNo" }

    /** Posts numbered past the read mark. Zero until the thread has been opened once. */
    val unread: Int
        get() = readUpTo?.let { mark -> postNos.count { it > mark } } ?: 0

    /** Neither archived nor pruned: refresh still has something to learn. */
    val isLive: Boolean
        get() = state == BookmarkState.ALIVE || state == BookmarkState.UNKNOWN

    val isDead: Boolean
        get() = state == BookmarkState.DEAD
}

package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost

data class ThreadContent(
    val details: ThreadDetails,
    val board: Board?,
    val bookmarked: Boolean,
    val revealAllSpoilers: Boolean,
    /** Post numbers revealed by tapping their spoiler. Key: postNo to spoiler id. */
    val revealedSpoilers: Set<Pair<Long, Int>>,
    val revealedImageSpoilers: Set<Long>,
    /** First post number after the "N new posts" divider; null = no divider. */
    val newPostsAfter: Long?,
    val newPostsCount: Int,
    val autoRefreshEnabled: Boolean,
    val archivedNotice: Boolean,
    /** A refresh failed while a thread was already on screen; shown once, then cleared. */
    val refreshError: NetworkError? = null,
    /** A manual or pull refresh is in flight; the thread stays on screen meanwhile. */
    val refreshing: Boolean = false,
    val searchQuery: String?,
    val searchMatches: List<Long>,
    val searchIndex: Int,
    /** Stack of preview cards (D11); each entry is a post in this thread. */
    val previewStack: List<List<ThreadPost>>,
    val pendingExternalUrl: String?,
    val confirmBeforeOpeningLinks: Boolean,
    val trustedDomains: Set<String>,
    /** Media URL → vault status, for the thumbnail download badges. */
    val mediaSaveStatuses: Map<String, MediaSaveStatus> = emptyMap(),
    /** Long-pressing a thumbnail saves it to the vault. */
    val holdToSave: Boolean = true,
)

/** One-shot scroll request resolved by the ViewModel; the screen obeys and reports back. */
data class ScrollTarget(val postNo: Long, val animate: Boolean)

/**
 * Everything the user has done in this thread since opening it. One flow, mutated with
 * `update { it.copy(...) }`, so related fields (a new query and its reset index) change
 * in a single emission.
 */
data class Session(
    /** Text spoilers revealed by tapping, as (postNo, spoiler id). */
    val revealedText: Set<Pair<Long, Int>> = emptySet(),
    val revealedImages: Set<Long> = emptySet(),
    val searchQuery: String? = null,
    val searchIndex: Int = 0,
    /** Quotelink preview stack, each entry a group of post numbers. */
    val previewPostNos: List<List<Long>> = emptyList(),
    val pendingExternalUrl: String? = null,
    /** (last post before the divider, count of posts after it); null = no divider. */
    val newPostsAfter: Pair<Long, Int>? = null,
    /** The thread 404ed during a refresh. */
    val archived: Boolean = false,
    val autoRefreshOverride: Boolean? = null,
    val refreshError: NetworkError? = null,
    val refreshing: Boolean = false,
)

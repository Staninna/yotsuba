package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost

data class ThreadContent(
    val details: ThreadDetails,
    val board: Board?,
    val bookmarked: Boolean,
    val revealAllSpoilers: Boolean,
    /** Per-post display state, keyed by post number; missing means [PostUiState.Default]. */
    val postStates: Map<Long, PostUiState>,
    /** What the list shows, top to bottom: posts with the "N new posts" divider in place. */
    val rows: List<ThreadRow>,
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
    /** Suffix shown after a quotelink to these posts, e.g. ">>123 (OP)". */
    val quoteLabels: Map<Long, QuoteLabel> = emptyMap(),
    /** Posts the user marked as theirs. */
    val claimedPostNos: Set<Long> = emptySet(),
    /** Posts (not themselves claimed) that quote a claimed post. */
    val repliesToMe: Int = 0,
)

/** Why a quotelink target is special; the screen picks the words. */
enum class QuoteLabel { OP, YOU }

/** What one post card needs beyond the post itself, computed once per emission. */
data class PostUiState(
    val revealedSpoilerIds: Set<Int> = emptySet(),
    val imageSpoilerRevealed: Boolean = false,
    /** Posts quoting this one, in thread order. */
    val backlinks: List<Long> = emptyList(),
    val saveStatus: MediaSaveStatus? = null,
    /** Briefly true after a quotelink jump landed on this post. */
    val highlighted: Boolean = false,
) {
    companion object {
        val Default = PostUiState()
    }
}

/** One list item on the thread screen; the VM decides the order, the screen only draws. */
sealed interface ThreadRow {
    data class Post(val post: ThreadPost) : ThreadRow

    /** Sits before the first post that arrived in a refresh; tapping it dismisses it. */
    data class NewPostsDivider(val count: Int) : ThreadRow
}

/** Where a tapped link goes; the VM applies the trusted-domain policy (D26). */
sealed interface LinkAction {
    data class Internal(val link: Urls.InternalLink) : LinkAction
    data class External(val url: String) : LinkAction

    /** The confirmation dialog is now pending in the session; nothing to open yet. */
    data object Confirm : LinkAction
}

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
    /** The post a quotelink jump just landed on; cleared after a short delay. */
    val highlightedPostNo: Long? = null,
    val refreshError: NetworkError? = null,
    val refreshing: Boolean = false,
)

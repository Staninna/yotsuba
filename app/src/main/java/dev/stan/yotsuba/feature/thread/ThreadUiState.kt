package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.domain.model.ArchiveSource
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.NetworkError
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
    /** Where this copy lives when it came from a third-party archive; "Open in browser" goes there. */
    val archiveUrl: String? = null,
    /** Set when the posts are the vault's copy; epoch millis of when it was taken, if known. */
    val offlineCopyAt: Long? = null,
    /** A refresh failed while a thread was already on screen; shown once, then cleared. */
    val refreshError: NetworkError? = null,
    /** A manual or pull refresh is in flight; the thread stays on screen meanwhile. */
    val refreshing: Boolean = false,
    val searchQuery: String?,
    val searchMatches: List<Long>,
    val searchIndex: Int,
    /** The quote preview sheet, when one is open. */
    val preview: PreviewSheet? = null,
    val pendingExternalUrl: String?,
    /** Suffix shown after a quotelink to these posts, e.g. ">>123 (OP)". */
    val quoteLabels: Map<Long, QuoteLabel> = emptyMap(),
    /** Posts the user marked as theirs. */
    val claimedPostNos: Set<Long> = emptySet(),
    /** Posts (not themselves claimed) that quote a claimed post. */
    val repliesToMe: Int = 0,
    /** The newest of those, the post the "replies to you" indicator stands for. */
    val latestReplyToMe: Long? = null,
    /** Only posts by this poster ID are in [rows]; null shows everything. */
    val filterPosterId: String? = null,
    /** The thread gallery sheet is up; [mediaPosts] feeds it. */
    val galleryOpen: Boolean = false,
    /** The post whose long-press sheet is up. */
    val postSheet: ThreadPost? = null,
    val treeView: Boolean = false,
    /** Posts a content filter hid or stubbed; the top bar shows it when non-zero. */
    val filteredCount: Int = 0,
    /** Posts with a present attachment, in thread order. */
    val mediaPosts: List<ThreadPost> = emptyList(),
)

/**
 * The quote preview sheet. [path] is every post focused so far, oldest first; the last one
 * is the post the sheet is about. A post from another thread (or a pruned one recovered
 * from a saved or archived copy) is a "ghost": [ghost] names where it came from, and the
 * sheet passes through [Loading] and possibly [Missing] while it is looked up.
 */
sealed interface PreviewSheet {
    val path: List<Long>
    /** Null while the focused post belongs to the thread on screen. */
    val ghost: Ghost?
    val canGoBack: Boolean get() = path.size > 1
    val focusNo: Long get() = path.last()

    /** A small thread around the focused post: what it quotes above, what quotes it below. */
    data class Post(
        val focus: ThreadPost,
        val parents: List<ThreadPost>,
        val replies: List<ThreadPost>,
        override val path: List<Long>,
        override val ghost: Ghost? = null,
    ) : PreviewSheet

    /** The ghost's thread is being fetched. */
    data class Loading(override val path: List<Long>, override val ghost: Ghost) : PreviewSheet

    /** No copy of the ghost's thread holds the post; [error] says why the lookup stopped. */
    data class Missing(
        override val path: List<Long>,
        override val ghost: Ghost,
        val error: NetworkError,
    ) : PreviewSheet
}

/** Where a ghost post lives and which copy of that thread it was read from. */
data class Ghost(val board: String, val threadNo: Long, val source: GhostSource?)

sealed interface GhostSource {
    data object Live : GhostSource
    data object Saved : GhostSource
    data class Archived(val archive: ArchiveSource) : GhostSource

    companion object {
        fun of(details: ThreadDetails): GhostSource = when {
            details.offlineCopy -> Saved
            details.archive != null -> Archived(details.archive)
            else -> Live
        }
    }
}

/** A thread by address; what the ghost cache is keyed on. */
data class ThreadKey(val board: String, val threadNo: Long)

/** One step of the preview stack: a post, and the thread it is read in. */
data class PreviewRef(val board: String, val threadNo: Long, val postNo: Long) {
    val key: ThreadKey get() = ThreadKey(board, threadNo)
}

/** What the ViewModel knows about a thread looked up for a ghost post. */
sealed interface GhostState {
    data object Loading : GhostState
    data class Loaded(val details: ThreadDetails) : GhostState
    data class Failed(val error: NetworkError) : GhostState
}

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
    /** How many posts in the thread share this post's poster ID; 0 without an ID. */
    val posterIdCount: Int = 0,
    /** OP only: the thread is closed / stickied. */
    val closed: Boolean = false,
    val sticky: Boolean = false,
    /** Set while the full image is shown in the card instead of its thumbnail. */
    val inlineImage: InlineImage? = null,
) {
    val imageExpanded: Boolean get() = inlineImage != null

    companion object {
        val Default = PostUiState()
    }
}

/** What an expanded card needs to draw the full image: where the bytes come from and whether to ask first. */
data class InlineImage(
    /** The vault file when the image is already saved, so nothing is fetched twice. */
    val localPath: String? = null,
    /** Data saver is on; on a metered connection the card shows "Load" instead of fetching. */
    val dataSaver: Boolean = false,
)

/** One list item on the thread screen; the VM decides the order, the screen only draws. */
sealed interface ThreadRow {
    /** [depth] is the tree-view indent level; always 0 in the linear view. */
    data class Post(val post: ThreadPost, val depth: Int = 0) : ThreadRow

    /** Sits before the first post that arrived in a refresh; tapping it dismisses it. */
    data class NewPostsDivider(val count: Int) : ThreadRow

    /** Tree view: [count] replies nested deeper than the cap under [parentNo]; tap expands them. */
    data class MoreReplies(val parentNo: Long, val count: Int) : ThreadRow

    /** A post a STUB filter caught: one line naming [pattern]; tap opens the post in place. */
    data class Filtered(val postNo: Long, val pattern: String, val depth: Int = 0) : ThreadRow
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
    /** Posts whose full image is shown in place of the thumbnail. */
    val expandedImages: Set<Long> = emptySet(),
    val searchQuery: String? = null,
    val searchIndex: Int = 0,
    /** Posts focused in the preview sheet, oldest first; empty means no sheet. */
    val previewPath: List<PreviewRef> = emptyList(),
    /** Threads fetched for ghost posts, kept for the life of the screen. */
    val ghosts: Map<ThreadKey, GhostState> = emptyMap(),
    val pendingExternalUrl: String? = null,
    /** (last post before the divider, count of posts after it); null = no divider. */
    val newPostsAfter: Pair<Long, Int>? = null,
    /** The thread 404ed during a refresh. */
    val archived: Boolean = false,
    val autoRefreshOverride: Boolean? = null,
    /** The post a quotelink jump just landed on; cleared after a short delay. */
    val highlightedPostNo: Long? = null,
    /** Show only this poster ID's posts. */
    val filterPosterId: String? = null,
    val galleryOpen: Boolean = false,
    val postSheetFor: Long? = null,
    /** Threaded (indented) layout instead of the flat list. */
    val treeView: Boolean = false,
    /** Depth-capped subtrees the user expanded, by the post at the cap. */
    val expandedTails: Set<Long> = emptySet(),
    /** Stubbed posts the user opened. */
    val expandedFiltered: Set<Long> = emptySet(),
    val refreshError: NetworkError? = null,
    val refreshing: Boolean = false,
    /** When the vault copy on screen was taken; null until one is shown. */
    val offlineCopyAt: Long? = null,
)

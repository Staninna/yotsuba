package dev.stan.yotsuba.feature.thread

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
)

/** One-shot scroll request resolved by the ViewModel; the screen obeys and reports back. */
data class ScrollTarget(val postNo: Long, val animate: Boolean)

/** Text and image spoilers the user has revealed by tapping. */
data class SpoilerState(
    val revealedText: Set<Pair<Long, Int>>,
    val revealedImages: Set<Long>,
)

/** Raw in-thread search input; matches are derived against the loaded posts. */
data class SearchInput(val query: String?, val index: Int)

/** Quotelink preview stack (post numbers) and the pending external-link confirmation. */
data class OverlayState(
    val previewPostNos: List<List<Long>>,
    val pendingExternalUrl: String?,
)

/** Auto-refresh bookkeeping: the new-posts divider, archived flag, and the user's override. */
data class RefreshState(
    val newPostsAfter: Pair<Long, Int>?,
    val archived: Boolean,
    val autoRefreshOverride: Boolean?,
)

/** Slow-changing companions of the thread itself. */
data class MetaState(
    val board: Board?,
    val bookmarked: Boolean,
    val mediaSaveStatuses: Map<String, MediaSaveStatus>,
)

/** Everything the user has done in this session, grouped for one typed top-level combine. */
data class SessionState(
    val spoilers: SpoilerState,
    val search: SearchInput,
    val overlays: OverlayState,
    val refresh: RefreshState,
)

package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost

sealed interface ThreadUiState {
    data object Loading : ThreadUiState
    data class Error(val error: NetworkError) : ThreadUiState
    data class Success(
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
        val mediaSaveStatuses: Map<String, dev.stan.yotsuba.domain.model.MediaSaveStatus> = emptyMap(),
    ) : ThreadUiState
}

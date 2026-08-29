package dev.stan.yotsuba.feature.media

import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.PostGraph
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost

/**
 * The conversation behind whatever is on screen, and everything the reply panel needs to
 * render it.
 *
 * Deliberately says nothing about where the posts came from: the live thread and the
 * posts.json snapshot beside saved media produce the same thing, which is what lets the
 * vault show replies at all. [posts] keep thread order; [byNo] is the lookup.
 */
data class ViewerThread(
    val posts: List<ThreadPost> = emptyList(),
    val backlinks: Map<Long, List<Long>> = emptyMap(),
    val board: Board? = null,
) {
    val byNo: Map<Long, ThreadPost> by lazy(LazyThreadSafetyMode.NONE) { posts.associateBy { it.no } }

    val graph: PostGraph by lazy(LazyThreadSafetyMode.NONE) { PostGraph(posts, backlinks) }

    /** False when nothing was captured, so a viewer can hide the affordance entirely. */
    val hasPosts: Boolean get() = posts.isNotEmpty()

    companion object {
        fun of(details: ThreadDetails?, board: Board? = null): ViewerThread = ViewerThread(
            posts = details?.posts.orEmpty(),
            backlinks = details?.backlinks.orEmpty(),
            board = board,
        )
    }
}

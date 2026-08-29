package dev.stan.yotsuba.domain.model

/**
 * Who quotes whom in a thread: `quoted post number -> the posts quoting it`, the reverse
 * of each post's [ThreadPost.quotedPostNos] (D11).
 *
 * Pure, so both the network mapper and anything rebuilding a thread from disk derive
 * backlinks the same way instead of each rolling its own walk.
 */
fun backlinksOf(posts: List<ThreadPost>): Map<Long, List<Long>> {
    val backlinks = mutableMapOf<Long, MutableList<Long>>()
    for (post in posts) {
        for (quoted in post.quotedPostNos) {
            backlinks.getOrPut(quoted) { mutableListOf() } += post.no
        }
    }
    return backlinks
}

/**
 * Traversal over a thread's quote graph in both directions: the posts that reply to a
 * post, and the posts it replies to.
 *
 * Both walks are transitive and cycle-safe — a thread can quote in circles, and 4chan
 * post numbers are only monotonic within a board, so nothing here may assume ordering.
 */
class PostGraph(
    private val byNo: Map<Long, ThreadPost>,
    private val backlinks: Map<Long, List<Long>>,
) {
    /** Every post that transitively replies to [postNo], in post order. */
    fun descendantsOf(postNo: Long): List<ThreadPost> =
        walk(postNo) { backlinks[it].orEmpty() }

    /** Every post [postNo] transitively quotes, in post order. */
    fun ancestorsOf(postNo: Long): List<ThreadPost> =
        walk(postNo) { byNo[it]?.quotedPostNos.orEmpty() }

    /**
     * The post itself plus everything above and below it — the conversation a reader would
     * need to make sense of it on its own, long after the thread is gone.
     */
    fun conversationAround(postNo: Long): List<ThreadPost> {
        val nos = buildSet {
            byNo[postNo]?.let { add(it.no) }
            ancestorsOf(postNo).forEach { add(it.no) }
            descendantsOf(postNo).forEach { add(it.no) }
        }
        return nos.sorted().mapNotNull { byNo[it] }
    }

    private inline fun walk(from: Long, next: (Long) -> List<Long>): List<ThreadPost> {
        val seen = linkedSetOf<Long>()
        val queue = ArrayDeque(next(from))
        while (queue.isNotEmpty()) {
            val no = queue.removeFirst()
            if (no != from && seen.add(no)) queue.addAll(next(no))
        }
        return seen.sorted().mapNotNull { byNo[it] }
    }

    companion object {
        fun of(details: ThreadDetails): PostGraph =
            PostGraph(details.posts.associateBy { it.no }, details.backlinks)
    }
}

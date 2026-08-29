package dev.stan.yotsuba.domain.model

/**
 * Traversal over a thread's quote graph in both directions: the posts that reply to a
 * post, and the posts it replies to.
 *
 * Both walks are transitive and cycle-safe. Results come back in thread order (the
 * position in [posts]), never by post number: 4chan numbers are only monotonic within a
 * board, and a thread rebuilt from disk may carry posts in any order.
 */
class PostGraph(
    private val posts: List<ThreadPost>,
    private val backlinks: Map<Long, List<Long>>,
) {
    /** Map form kept for the media viewer; iteration order of the map is the thread order. */
    constructor(byNo: Map<Long, ThreadPost>, backlinks: Map<Long, List<Long>>) :
        this(byNo.values.toList(), backlinks)

    private val byNo: Map<Long, ThreadPost> = posts.associateBy { it.no }
    private val indexOf: Map<Long, Int> = posts.withIndex().associate { (i, p) -> p.no to i }

    /** Every post that transitively replies to [postNo], in thread order. */
    fun descendantsOf(postNo: Long): List<ThreadPost> =
        walk(postNo) { backlinks[it].orEmpty() }

    /** Every post [postNo] transitively quotes, in thread order. */
    fun ancestorsOf(postNo: Long): List<ThreadPost> =
        walk(postNo) { byNo[it]?.quotedPostNos.orEmpty() }

    /** Direct replies to [postNo], in thread order. */
    fun repliesTo(postNo: Long): List<ThreadPost> =
        backlinks[postNo].orEmpty().mapNotNull { byNo[it] }.sortedBy { indexOf[it.no] }

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
        return inThreadOrder(nos)
    }

    /**
     * The thread as a forest: each post nested under the first post it quotes that appears
     * earlier in the thread. Posts quoting nothing in-thread sit at the top level. [maxDepth]
     * flattens anything deeper onto the last allowed level so the indent stays readable.
     */
    fun tree(maxDepth: Int = Int.MAX_VALUE): List<TreeNode> {
        val depthOf = HashMap<Long, Int>()
        return posts.map { post ->
            val parent = post.quotedPostNos.firstOrNull { q ->
                val pi = indexOf[q]
                pi != null && pi < indexOf.getValue(post.no)
            }
            val depth = if (parent == null || post.isOp) 0 else (depthOf.getValue(parent) + 1)
            depthOf[post.no] = depth
            TreeNode(post, depth.coerceAtMost(maxDepth), parent)
        }
    }

    /** A post with its nesting depth under [parentNo] in [tree]. */
    data class TreeNode(val post: ThreadPost, val depth: Int, val parentNo: Long?)

    private fun inThreadOrder(nos: Collection<Long>): List<ThreadPost> =
        nos.mapNotNull { byNo[it] }.sortedBy { indexOf[it.no] }

    private inline fun walk(from: Long, next: (Long) -> List<Long>): List<ThreadPost> {
        val seen = linkedSetOf<Long>()
        val queue = ArrayDeque(next(from))
        while (queue.isNotEmpty()) {
            val no = queue.removeFirst()
            if (no != from && seen.add(no)) queue.addAll(next(no))
        }
        return inThreadOrder(seen)
    }

    companion object {
        fun of(details: ThreadDetails): PostGraph = PostGraph(details.posts, details.backlinks)

        /**
         * Who quotes whom: `quoted post number -> the posts quoting it`, the reverse of each
         * post's [ThreadPost.quotedPostNos] (D11). Pure, so the network mapper and anything
         * rebuilding a thread from disk derive backlinks the same way.
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
    }
}

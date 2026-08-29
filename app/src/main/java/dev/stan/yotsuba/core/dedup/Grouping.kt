package dev.stan.yotsuba.core.dedup

/**
 * Pure grouping over hashes. Exact grouping is a bucket by key; near grouping uses the
 * pigeonhole trick: two 64-bit hashes within Hamming distance `d` agree on at least one
 * of `d + 1` disjoint chunks, so only hashes sharing a chunk are ever compared.
 */
object Grouping {
    /** Items sharing a key, keeping only keys held by two or more; input order within a group. */
    fun <T, K : Any> exact(items: List<T>, key: (T) -> K?): List<List<T>> =
        items.groupBy { key(it) }
            .filterKeys { it != null }
            .values
            .filter { it.size > 1 }

    /**
     * Connected components of items whose hashes are within [maxDistance] of each other.
     * Transitive: a chain a~b~c lands in one group even when a and c are further apart.
     */
    fun <T> near(items: List<T>, maxDistance: Int, hash: (T) -> Long?): List<List<T>> {
        val indices = ArrayList<Int>()
        val hashes = LongArray(items.size)
        items.forEachIndexed { i, item -> hash(item)?.let { hashes[i] = it; indices += i } }
        if (indices.size < 2) return emptyList()

        val parent = IntArray(items.size) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) { val n = parent[c]; parent[c] = r; c = n }
            return r
        }

        val chunks = (maxDistance + 1).coerceIn(1, 16)
        val bits = 64 / chunks
        for (c in 0 until chunks) {
            val shift = c * bits
            val width = if (c == chunks - 1) 64 - shift else bits
            val mask = if (width >= 64) -1L else (1L shl width) - 1
            val bucket = HashMap<Long, MutableList<Int>>()
            for (i in indices) bucket.getOrPut((hashes[i] ushr shift) and mask) { ArrayList(2) } += i
            for (members in bucket.values) {
                if (members.size < 2) continue
                for (a in members.indices) for (b in a + 1 until members.size) {
                    val ia = members[a]
                    val ib = members[b]
                    val ra = find(ia)
                    val rb = find(ib)
                    if (ra != rb && DHash.distance(hashes[ia], hashes[ib]) <= maxDistance) parent[ra] = rb
                }
            }
        }
        return indices.groupBy { find(it) }
            .values
            .filter { it.size > 1 }
            .map { group -> group.map { items[it] } }
    }
}

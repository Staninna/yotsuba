package dev.stan.yotsuba.feature.catalog

import dev.stan.yotsuba.domain.model.CatalogThread

/** How one thread is tied to the others in its catalog by quotelinks. */
data class CrossReferences(val linksTo: Int, val referencedBy: Int)

/**
 * Per thread number, how many other catalog threads it quotes and how many quote it. Quotes
 * into threads that are not in [threads] (pruned, archived, another page) count for nothing,
 * so every number here is one the user can actually tap through to. Threads with neither are
 * left out.
 */
fun crossReferences(threads: List<CatalogThread>): Map<Long, CrossReferences> {
    val present = threads.mapTo(HashSet()) { it.no }
    val linksTo = HashMap<Long, Int>()
    val referencedBy = HashMap<Long, Int>()
    for (t in threads) {
        for (q in t.quotedThreadNos) {
            if (q !in present) continue
            linksTo.merge(t.no, 1, Int::plus)
            referencedBy.merge(q, 1, Int::plus)
        }
    }
    return (linksTo.keys + referencedBy.keys).associateWith {
        CrossReferences(linksTo = linksTo[it] ?: 0, referencedBy = referencedBy[it] ?: 0)
    }
}

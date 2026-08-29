package dev.stan.yotsuba.core.dedup

import dev.stan.yotsuba.domain.model.DuplicateEntry

/** The file worth keeping out of a group: most pixels, then most bytes, then saved first. */
object Keeper {
    val order: Comparator<DuplicateEntry> =
        compareByDescending<DuplicateEntry> { it.pixelSize }
            .thenByDescending { it.sizeBytes }
            .thenBy { it.savedAt }

    fun suggest(entries: List<DuplicateEntry>): DuplicateEntry = entries.minWith(order)
}

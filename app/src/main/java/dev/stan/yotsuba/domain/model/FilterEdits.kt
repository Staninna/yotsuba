package dev.stan.yotsuba.domain.model

/**
 * The four edits the filters screen makes to [Settings.filters]. All match by [Filter.id],
 * which is a UUID and therefore unique in the list, so structural and id equality agree.
 */

fun Settings.removeFilter(id: String): Settings = copy(filters = filters.filterNot { it.id == id })

/** Re-inserts an undone delete at its old position, clamped so a shrunken list still takes it. */
fun Settings.insertFilter(at: Int, filter: Filter): Settings {
    val index = at.coerceAtMost(filters.size)
    return copy(filters = filters.toMutableList().apply { add(index, filter) })
}

fun Settings.setFilterEnabled(id: String, enabled: Boolean): Settings =
    copy(filters = filters.map { if (it.id == id) it.copy(enabled = enabled) else it })

/** Replaces the filter with the same id, or appends when there is none. */
fun Settings.upsertFilter(filter: Filter): Settings =
    if (filters.any { it.id == filter.id }) copy(filters = filters.map { if (it.id == filter.id) filter else it })
    else copy(filters = filters + filter)

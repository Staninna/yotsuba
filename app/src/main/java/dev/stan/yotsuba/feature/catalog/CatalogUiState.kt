package dev.stan.yotsuba.feature.catalog

import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.CatalogSort
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.Filter

data class CatalogContent(
    val threads: List<CatalogThread>,
    val layout: CatalogLayout,
    val sort: CatalogSort = CatalogSort.BUMP_ORDER,
    /** Threads whose thumbnail is still hidden behind a blur; empty when the board does not blur. */
    val blurred: Set<Long> = emptySet(),
    /** null = search closed. */
    val searchQuery: String?,
    val refreshing: Boolean,
    val offline: Boolean,
    /** The board's own description, shown once per session at the top of the list; null once dismissed. */
    val about: String? = null,
    /** Threads still in [threads] but collapsed to a stub, keyed by thread number. */
    val stubs: Map<Long, Filter> = emptyMap(),
    /** Threads a filter hid outright plus the ones stubbed; for the top bar count. */
    val filteredCount: Int = 0,
    /** Replies since the user last read each visited thread, keyed by thread number. */
    val newReplies: Map<Long, NewReplies> = emptyMap(),
)

package dev.stan.yotsuba.feature.catalog

import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.Filter

data class CatalogContent(
    val threads: List<CatalogThread>,
    val layout: CatalogLayout,
    /** null = search closed. */
    val searchQuery: String?,
    val refreshing: Boolean,
    val offline: Boolean,
    /** Threads still in [threads] but collapsed to a stub, keyed by thread number. */
    val stubs: Map<Long, Filter> = emptyMap(),
    /** Threads a filter hid outright plus the ones stubbed; for the top bar count. */
    val filteredCount: Int = 0,
)

package dev.stan.yotsuba.feature.catalog

import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.CatalogThread

data class CatalogContent(
    val threads: List<CatalogThread>,
    val layout: CatalogLayout,
    /** null = search closed. */
    val searchQuery: String?,
    val refreshing: Boolean,
    val offline: Boolean,
)

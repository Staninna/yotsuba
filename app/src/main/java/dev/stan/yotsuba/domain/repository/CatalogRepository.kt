package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.domain.model.CatalogThread

interface CatalogRepository {
    suspend fun catalog(board: String, forceRefresh: Boolean = false): DataResult<List<CatalogThread>>
}

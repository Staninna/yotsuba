package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.DataResult

interface CatalogRepository {
    suspend fun catalog(board: String, forceRefresh: Boolean = false): DataResult<List<CatalogThread>>
}

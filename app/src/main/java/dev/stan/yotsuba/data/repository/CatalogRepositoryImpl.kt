package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.network.FourChanApi
import dev.stan.yotsuba.core.util.apiResult
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.repository.CatalogRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepositoryImpl @Inject constructor(
    private val api: FourChanApi,
) : CatalogRepository {

    override suspend fun catalog(board: String, forceRefresh: Boolean): DataResult<List<CatalogThread>> =
        apiResult {
            val pages = api.catalog(board, cacheControl = if (forceRefresh) "no-cache" else null)
            pages.flatMap { it.threads }.map { it.toCatalogThread(board) }
        }
}

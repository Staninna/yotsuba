package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.network.FourChanApi
import dev.stan.yotsuba.core.text.PostHtmlParser
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.toNetworkError
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.repository.CatalogRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CatalogRepositoryImpl @Inject constructor(
    private val api: FourChanApi,
    private val parser: PostHtmlParser,
) : CatalogRepository {

    override suspend fun catalog(board: String, forceRefresh: Boolean): DataResult<List<CatalogThread>> =
        withContext(Dispatchers.IO) {
            try {
                val pages = api.catalog(board, cacheControl = if (forceRefresh) "no-cache" else null)
                DataResult.Success(pages.flatMap { it.threads }.map { it.toCatalogThread(board, parser) })
            } catch (e: Exception) {
                DataResult.Failure(e.toNetworkError())
            }
        }
}

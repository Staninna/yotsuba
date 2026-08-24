package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.network.FourChanApi
import dev.stan.yotsuba.core.text.PostHtmlParser
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.toNetworkError
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.repository.ThreadRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ThreadRepositoryImpl @Inject constructor(
    private val api: FourChanApi,
    private val parser: PostHtmlParser,
) : ThreadRepository {

    override suspend fun thread(board: String, no: Long, forceRefresh: Boolean): DataResult<ThreadDetails> =
        withContext(Dispatchers.IO) {
            try {
                val dto = api.thread(board, no, cacheControl = if (forceRefresh) "no-cache" else null)
                val posts = dto.posts.map { it.toThreadPost(board, parser) }
                val op = dto.posts.firstOrNull()
                DataResult.Success(
                    buildThreadDetails(board, no, posts).copy(
                        archived = op?.archived == 1,
                        closed = op?.closed == 1,
                    )
                )
            } catch (e: Exception) {
                DataResult.Failure(e.toNetworkError())
            }
        }
}

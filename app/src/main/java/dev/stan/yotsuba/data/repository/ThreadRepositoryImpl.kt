package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.network.FourChanApi
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.apiResult
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.repository.ThreadRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreadRepositoryImpl @Inject constructor(
    private val api: FourChanApi,
) : ThreadRepository {

    override suspend fun thread(board: String, no: Long, forceRefresh: Boolean): DataResult<ThreadDetails> =
        apiResult {
            val dto = api.thread(board, no, cacheControl = if (forceRefresh) "no-cache" else null)
            val posts = dto.posts.map { it.toThreadPost(board) }
            val op = dto.posts.firstOrNull()
            buildThreadDetails(
                board, no, posts,
                archived = op?.archived == 1,
                closed = op?.closed == 1,
            )
        }
}

package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.network.ArchiveApi
import dev.stan.yotsuba.core.network.ArchiveHosts
import dev.stan.yotsuba.core.network.FourChanApi
import dev.stan.yotsuba.core.network.dto.parseFoolFuukaThread
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.core.util.apiResult
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.repository.ThreadRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreadRepositoryImpl @Inject constructor(
    private val api: FourChanApi,
    private val archiveApi: ArchiveApi,
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
                sticky = op?.sticky == 1,
            )
        }

    override suspend fun archivedThread(board: String, no: Long): DataResult<ThreadDetails> {
        val source = ArchiveHosts.sourceFor(board) ?: return DataResult.Failure(NetworkError.NotFound)
        val url = ArchiveHosts.apiUrl(source, board, no) ?: return DataResult.Failure(NetworkError.NotFound)
        return when (val r = apiResult { parseFoolFuukaThread(archiveApi.thread(url)) }) {
            is DataResult.Failure -> r
            is DataResult.Success -> r.value
                ?.let { DataResult.Success(it.toThreadDetails(board, source)) }
                ?: DataResult.Failure(NetworkError.NotFound)
        }
    }
}

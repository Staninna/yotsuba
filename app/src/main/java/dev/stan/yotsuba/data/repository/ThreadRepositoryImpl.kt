package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.network.ArchiveApi
import dev.stan.yotsuba.core.network.ArchiveHosts
import dev.stan.yotsuba.core.network.FourChanApi
import dev.stan.yotsuba.core.network.dto.parseFoolFuukaThread
import dev.stan.yotsuba.core.util.apiResult
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.UsageKind
import dev.stan.yotsuba.domain.repository.ThreadRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreadRepositoryImpl @Inject constructor(
    private val api: FourChanApi,
    private val archiveApi: ArchiveApi,
    private val usage: UsageRecorder,
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

    /**
     * Every archive that carries the board, in [ArchiveHosts] order, until one has the
     * thread. "Not found" and a failed request move on to the next; a rate limit stops the
     * chain, since hammering the next host is how the next host rate-limits too.
     */
    override suspend fun archivedThread(board: String, no: Long): DataResult<ThreadDetails> {
        var last: DataResult<ThreadDetails> = DataResult.Failure(NetworkError.NotFound)
        for (source in ArchiveHosts.sourcesFor(board)) {
            val url = ArchiveHosts.apiUrl(source, board, no) ?: continue
            val r = apiResult { parseFoolFuukaThread(archiveApi.thread(url)) }
            val thread = (r as? DataResult.Success)?.value
            if (thread != null) {
                usage.record(UsageKind.ARCHIVE_RESCUE, board, no)
                return DataResult.Success(thread.toThreadDetails(board, source))
            }
            if (r is DataResult.Failure) {
                if (r.error == NetworkError.RateLimited) return r
                last = r
            }
        }
        return last
    }
}

package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.domain.model.ThreadDetails

interface ThreadRepository {
    suspend fun thread(board: String, no: Long, forceRefresh: Boolean = false): DataResult<ThreadDetails>

    /**
     * The thread as a third-party archive remembers it, for when 4chan has dropped it.
     * [NetworkError.NotFound] when no archive carries the board or none has the thread.
     * The result reports its archive in [ThreadDetails.archive].
     */
    suspend fun archivedThread(board: String, no: Long): DataResult<ThreadDetails> =
        DataResult.Failure(NetworkError.NotFound)
}

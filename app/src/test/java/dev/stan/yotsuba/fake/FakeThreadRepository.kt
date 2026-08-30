package dev.stan.yotsuba.fake

import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.repository.ThreadRepository

/** Serves [details] for the thread under test; [result] overrides it once set (a failure, say). */
class FakeThreadRepository(var details: ThreadDetails) : ThreadRepository {
    var result: DataResult<ThreadDetails>? = null
    var archived: DataResult<ThreadDetails> = DataResult.Failure(NetworkError.NotFound)
    /** Answers for other threads (ghost lookups), by (board, no); [result]/[archived] otherwise. */
    val byThread = mutableMapOf<Pair<String, Long>, DataResult<ThreadDetails>>()
    val archivedByThread = mutableMapOf<Pair<String, Long>, DataResult<ThreadDetails>>()
    /** Every source asked, in order, so a test can assert the fallback order. */
    val asked = mutableListOf<String>()

    override suspend fun thread(board: String, no: Long, forceRefresh: Boolean): DataResult<ThreadDetails> {
        asked += "live"
        return byThread[board to no] ?: result ?: DataResult.Success(details)
    }
    override suspend fun archivedThread(board: String, no: Long): DataResult<ThreadDetails> {
        asked += "archive"
        return archivedByThread[board to no] ?: archived
    }
}

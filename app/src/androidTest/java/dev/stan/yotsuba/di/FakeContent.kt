package dev.stan.yotsuba.di

import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.CatalogRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import javax.inject.Inject
import javax.inject.Singleton

/*
 * The read-only content sources: boards, catalogs, threads. Each starts on [TestSeed] and
 * exposes a [failWith] knob so a test can show the error, offline or rate-limited state
 * without touching the network. Set knobs in `seed()` (before launch) or mid-test and
 * pull to refresh; the next call answers with the failure.
 */

@Singleton
class FakeBoardRepository @Inject constructor() : BoardRepository {
    val boards = TestSeed.boards.toMutableList()
    var failWith: NetworkError? = null
    var calls = 0

    override suspend fun boards(forceRefresh: Boolean): DataResult<List<Board>> {
        calls++
        failWith?.let { return DataResult.Failure(it) }
        return DataResult.Success(boards.toList())
    }

    override suspend fun board(code: String): Board? = boards.firstOrNull { it.code == code }
}

@Singleton
class FakeCatalogRepository @Inject constructor() : CatalogRepository {
    val catalogs: MutableMap<String, List<CatalogThread>> = TestSeed.catalogs.toMutableMap()
    var failWith: NetworkError? = null
    /** Answer from cache: the catalog renders with the offline banner. */
    var fromCache = false
    val calls = mutableListOf<Pair<String, Boolean>>()

    override suspend fun catalog(board: String, forceRefresh: Boolean): DataResult<List<CatalogThread>> {
        calls += board to forceRefresh
        failWith?.let { return DataResult.Failure(it) }
        return DataResult.Success(catalogs[board].orEmpty(), fromCache = fromCache)
    }
}

@Singleton
class FakeThreadRepository @Inject constructor() : ThreadRepository {
    val threads: MutableMap<Pair<String, Long>, ThreadDetails> = TestSeed.threads.toMutableMap()
    val archived: MutableMap<Pair<String, Long>, ThreadDetails> = TestSeed.archivedThreads.toMutableMap()
    /** Every live fetch fails with this; the archive fallback still answers. */
    var failWith: NetworkError? = null
    var fromCache = false
    val calls = mutableListOf<Triple<String, Long, Boolean>>()
    val archiveCalls = mutableListOf<Pair<String, Long>>()

    override suspend fun thread(board: String, no: Long, forceRefresh: Boolean): DataResult<ThreadDetails> {
        calls += Triple(board, no, forceRefresh)
        failWith?.let { return DataResult.Failure(it) }
        val details = threads[board to no] ?: return DataResult.Failure(NetworkError.NotFound)
        return DataResult.Success(details, fromCache = fromCache)
    }

    override suspend fun archivedThread(board: String, no: Long): DataResult<ThreadDetails> {
        archiveCalls += board to no
        return archived[board to no]?.let { DataResult.Success(it) } ?: DataResult.Failure(NetworkError.NotFound)
    }
}

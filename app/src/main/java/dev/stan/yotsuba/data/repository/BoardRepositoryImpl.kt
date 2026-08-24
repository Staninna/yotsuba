package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.network.FourChanApi
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.toNetworkError
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.repository.BoardRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class BoardRepositoryImpl @Inject constructor(
    private val api: FourChanApi,
) : BoardRepository {

    private val mutex = Mutex()
    private var cached: List<Board>? = null

    override suspend fun boards(forceRefresh: Boolean): DataResult<List<Board>> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!forceRefresh) cached?.let { return@withContext DataResult.Success(it, fromCache = true) }
                try {
                    val dto = api.boards(cacheControl = if (forceRefresh) "no-cache" else null)
                    // The single place /f/ is excluded (D13): nothing can play .swf.
                    val boards = dto.boards.filter { it.board != "f" }.map { it.toDomain() }
                    cached = boards
                    DataResult.Success(boards)
                } catch (e: Exception) {
                    DataResult.Failure(e.toNetworkError())
                }
            }
        }

    override suspend fun board(code: String): Board? =
        (boards() as? DataResult.Success)?.value?.firstOrNull { it.code == code }
}

package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.network.FourChanApi
import dev.stan.yotsuba.core.util.apiResult
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.repository.BoardRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class BoardRepositoryImpl @Inject constructor(
    private val api: FourChanApi,
) : BoardRepository {

    private val mutex = Mutex()
    private var cached: List<Board>? = null
    private var byCode: Map<String, Board> = emptyMap()

    override suspend fun boards(forceRefresh: Boolean): DataResult<List<Board>> =
        mutex.withLock {
            if (!forceRefresh) cached?.let { return DataResult.Success(it, fromCache = true) }
            apiResult {
                val dto = api.boards(cacheControl = if (forceRefresh) "no-cache" else null)
                // The single place /f/ is excluded (D13): nothing can play .swf.
                val boards = dto.boards.filter { it.board != "f" }.map { it.toDomain() }
                cached = boards
                byCode = boards.associateBy { it.code }
                boards
            }
        }

    override suspend fun board(code: String): Board? =
        if (boards() is DataResult.Success) byCode[code] else null
}

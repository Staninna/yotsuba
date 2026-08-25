package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.database.dao.HiddenThreadDao
import dev.stan.yotsuba.core.database.entity.HiddenThreadEntity
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class HiddenThreadsRepositoryImpl @Inject constructor(
    private val dao: HiddenThreadDao,
) : HiddenThreadsRepository {

    override val all: Flow<List<HiddenThread>> =
        dao.all().map { list -> list.map { it.toDomain() } }

    override fun forBoard(board: String): Flow<List<HiddenThread>> =
        dao.forBoard(board).map { list -> list.map { it.toDomain() } }

    override suspend fun hide(board: String, threadNo: Long) =
        dao.hide(HiddenThreadEntity(board, threadNo, System.currentTimeMillis()))

    override suspend fun unhide(board: String, threadNo: Long) = dao.unhide(board, threadNo)

    private fun HiddenThreadEntity.toDomain() = HiddenThread(board = board, threadNo = threadNo)
}

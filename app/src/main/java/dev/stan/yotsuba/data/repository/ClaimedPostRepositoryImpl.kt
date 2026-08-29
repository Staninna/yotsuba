package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.database.dao.ClaimedPostDao
import dev.stan.yotsuba.core.database.entity.ClaimedPostEntity
import dev.stan.yotsuba.domain.repository.ClaimedPostRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ClaimedPostRepositoryImpl @Inject constructor(
    private val dao: ClaimedPostDao,
) : ClaimedPostRepository {

    override fun claimed(board: String, threadNo: Long): Flow<Set<Long>> =
        dao.forThread(board, threadNo).map { it.toSet() }

    override suspend fun claim(board: String, threadNo: Long, postNo: Long) =
        dao.claim(ClaimedPostEntity(board, threadNo, postNo, System.currentTimeMillis()))

    override suspend fun unclaim(board: String, threadNo: Long, postNo: Long) =
        dao.unclaim(board, threadNo, postNo)
}

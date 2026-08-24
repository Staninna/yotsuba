package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.database.dao.HistoryDao
import dev.stan.yotsuba.core.database.entity.HistoryEntity
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.repository.HistoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val dao: HistoryDao,
) : HistoryRepository {

    override val history: Flow<List<HistoryEntry>> =
        dao.all().map { list ->
            list.map {
                HistoryEntry(it.board, it.threadNo, it.subject, it.opExcerpt, it.thumbnailUrl, it.viewedAt, it.lastScrollPostNo)
            }
        }

    override suspend fun record(entry: HistoryEntry) = dao.record(
        HistoryEntity(
            entry.board, entry.threadNo, entry.subject, entry.opExcerpt, entry.thumbnailUrl,
            entry.viewedAt, entry.lastScrollPostNo,
        )
    )

    override suspend fun updateScrollPosition(board: String, threadNo: Long, postNo: Long) =
        dao.updateScroll(board, threadNo, postNo)

    override suspend fun updateReadUpTo(board: String, threadNo: Long, postNo: Long) =
        dao.updateMaxRead(board, threadNo, postNo)

    override suspend fun readUpTo(board: String, threadNo: Long): Long? = dao.maxRead(board, threadNo)

    override suspend fun lastScrollPosition(board: String, threadNo: Long): Long? =
        dao.lastScroll(board, threadNo)

    override suspend fun remove(board: String, threadNo: Long) = dao.delete(board, threadNo)

    override suspend fun clearAll() = dao.clearAll()

    override suspend fun trim(retainAfterMs: Long) = dao.trimOlderThan(retainAfterMs)
}

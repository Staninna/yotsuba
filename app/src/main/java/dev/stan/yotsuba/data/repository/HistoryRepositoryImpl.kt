package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.database.dao.HistoryDao
import dev.stan.yotsuba.core.database.entity.HistoryEntity
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.HistoryRetention
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val dao: HistoryDao,
    private val settingsRepository: SettingsRepository,
) : HistoryRepository {

    override val history: Flow<List<HistoryEntry>> =
        dao.all().map { list -> list.map { it.toDomainWithReadMark() } }

    override suspend fun record(entry: HistoryEntry) {
        dao.record(entry.toEntity())
        applyRetention()
    }

    /** Applies the retention preference on every write, so trimming never depends on a screen. */
    private suspend fun applyRetention() {
        val cutoff = when (settingsRepository.settings.first().historyRetention) {
            HistoryRetention.FOREVER -> return
            HistoryRetention.DAYS_30 -> System.currentTimeMillis() - 30L * 86_400_000
            HistoryRetention.DAYS_7 -> System.currentTimeMillis() - 7L * 86_400_000
        }
        dao.trimOlderThan(cutoff)
    }

    override suspend fun updateScrollPosition(board: String, threadNo: Long, postNo: Long) =
        dao.updateScroll(board, threadNo, postNo)

    override suspend fun updateReadUpTo(board: String, threadNo: Long, postNo: Long) =
        dao.updateMaxRead(board, threadNo, postNo)

    override suspend fun readUpTo(board: String, threadNo: Long): Long? = dao.maxRead(board, threadNo)

    override suspend fun lastScrollPosition(board: String, threadNo: Long): Long? =
        dao.lastScroll(board, threadNo)

    override suspend fun remove(board: String, threadNo: Long) = dao.delete(board, threadNo)

    override suspend fun restore(entry: HistoryEntry) {
        // Ignore-on-conflict: if the thread was revisited between remove and undo, the
        // fresher row wins over the stale snapshot.
        dao.insertIgnore(entry.toFullEntity())
    }

    override suspend fun clearAll() = dao.clearAll()

    override suspend fun trim(retainAfterMs: Long) = dao.trimOlderThan(retainAfterMs)
}

/** [toDomain] drops the read mark on purpose for callers that only record visits; history needs it. */
private fun HistoryEntity.toDomainWithReadMark(): HistoryEntry =
    toDomain().copy(maxReadPostNo = maxReadPostNo)

private fun HistoryEntry.toFullEntity(): HistoryEntity =
    toEntity().copy(maxReadPostNo = maxReadPostNo)

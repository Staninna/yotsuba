package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.database.dao.HistoryDao
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.HistoryRetention
import dev.stan.yotsuba.domain.model.UsageKind
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
    private val usage: UsageRecorder,
) : HistoryRepository {

    /** When each thread was last counted as a visit; every poll records history, only the first is a visit. */
    private val lastVisit = java.util.concurrent.ConcurrentHashMap<Pair<String, Long>, Long>()

    override val history: Flow<List<HistoryEntry>> =
        dao.all().map { list -> list.map { it.toDomain() } }

    override suspend fun record(entry: HistoryEntry) {
        dao.record(entry.toEntity())
        applyRetention()
        val key = entry.board to entry.threadNo
        if (entry.viewedAt - (lastVisit[key] ?: 0L) >= VISIT_GAP_MS) {
            lastVisit[key] = entry.viewedAt
            usage.record(UsageKind.THREAD_VISITED, entry.board, entry.threadNo)
        }
    }

    /** Applies the retention preference on every write, so trimming never depends on a screen. */
    private suspend fun applyRetention() {
        val cutoff = when (settingsRepository.settings.first().historyRetention) {
            HistoryRetention.FOREVER -> return
            HistoryRetention.DAYS_30 -> System.currentTimeMillis() - 30L * 86_400_000
            HistoryRetention.DAYS_7 -> System.currentTimeMillis() - 7L * 86_400_000
        }
        trim(cutoff)
    }

    override suspend fun updateScrollPosition(board: String, threadNo: Long, postNo: Long) =
        dao.updateScroll(board, threadNo, postNo)

    override suspend fun updateReadUpTo(board: String, threadNo: Long, postNo: Long) {
        if (dao.updateMaxRead(board, threadNo, postNo) > 0) usage.record(UsageKind.READ_MARK, board, threadNo, postNo)
    }

    override suspend fun readUpTo(board: String, threadNo: Long): Long? = dao.maxRead(board, threadNo)

    override suspend fun lastScrollPosition(board: String, threadNo: Long): Long? =
        dao.lastScroll(board, threadNo)

    override suspend fun remove(board: String, threadNo: Long) = dao.delete(board, threadNo)

    override suspend fun restore(entry: HistoryEntry) {
        // Ignore-on-conflict: if the thread was revisited between remove and undo, the
        // fresher row wins over the stale snapshot.
        dao.insertIgnore(entry.toEntity())
    }

    // The usage log carries the same board and thread numbers as history, so it is cleared
    // and trimmed with it. Without this, "Clear history" would leave the record behind.
    override suspend fun clearAll() {
        dao.clearAll()
        usage.clearAll()
    }

    override suspend fun trim(retainAfterMs: Long) {
        dao.trimOlderThan(retainAfterMs)
        usage.trim(retainAfterMs)
    }

    private companion object {
        const val VISIT_GAP_MS = 30 * 60_000L
    }
}

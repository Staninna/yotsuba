package dev.stan.yotsuba.data

import dev.stan.yotsuba.core.database.dao.HistoryDao
import dev.stan.yotsuba.core.database.entity.HistoryEntity
import dev.stan.yotsuba.core.database.entity.UsageEventEntity
import dev.stan.yotsuba.data.repository.HistoryRepositoryImpl
import dev.stan.yotsuba.data.repository.UsageRecorder
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.HistoryRetention
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.UsageKind
import dev.stan.yotsuba.fake.FakeSettings
import dev.stan.yotsuba.fake.FakeUsageEventDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The usage log is reading history too, so the history controls have to reach it. */
class HistoryUsageTest {

    private class FakeHistoryDao : HistoryDao {
        val rows = mutableListOf<HistoryEntity>()
        override fun all(): Flow<List<HistoryEntity>> = flowOf(rows.toList())
        override suspend fun updateVisit(
            board: String,
            threadNo: Long,
            subject: String?,
            opExcerpt: String,
            thumbnailUrl: String?,
            viewedAt: Long,
        ): Int = 0
        override suspend fun insertIgnore(entity: HistoryEntity) { rows += entity }
        override suspend fun lastScroll(board: String, threadNo: Long): Long? = null
        override suspend fun updateScroll(board: String, threadNo: Long, postNo: Long) = Unit
        override suspend fun updateMaxRead(board: String, threadNo: Long, postNo: Long): Int = 1
        override suspend fun maxRead(board: String, threadNo: Long): Long? = null
        override suspend fun delete(board: String, threadNo: Long) { rows.removeAll { it.threadNo == threadNo } }
        override suspend fun clearAll() { rows.clear() }
        override suspend fun trimOlderThan(cutoffMs: Long) { rows.removeAll { it.viewedAt < cutoffMs } }
    }

    private val usageDao = FakeUsageEventDao()

    private fun repository(retention: HistoryRetention) = HistoryRepositoryImpl(
        FakeHistoryDao(),
        FakeSettings(Settings(historyRetention = retention)),
        UsageRecorder(usageDao, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, Dispatchers.Unconfined),
    )

    private fun entry(threadNo: Long, viewedAt: Long) =
        HistoryEntry("g", threadNo, null, "op", null, viewedAt, null)

    @Test fun `clearing history clears the usage log with it`() = runTest {
        val repository = repository(HistoryRetention.FOREVER)
        repository.record(entry(1, System.currentTimeMillis()))
        assertTrue(usageDao.events.isNotEmpty())

        repository.clearAll()

        assertEquals(emptyList<Any>(), usageDao.events)
    }

    @Test fun `the retention setting bounds the usage log`() = runTest {
        val repository = repository(HistoryRetention.DAYS_7)
        val old = System.currentTimeMillis() - 30L * 86_400_000
        usageDao.events += UsageEventEntity(
            kind = UsageKind.THREAD_VISITED.name, board = "g", threadNo = 1, at = old, value = null,
        )

        // Retention is applied on the next write, the same moment history itself is trimmed.
        repository.record(entry(2, System.currentTimeMillis()))

        assertTrue(usageDao.events.none { it.at == old })
        assertEquals(1, usageDao.events.size)
    }
}

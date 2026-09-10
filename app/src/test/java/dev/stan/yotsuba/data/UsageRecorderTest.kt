package dev.stan.yotsuba.data

import dev.stan.yotsuba.core.database.dao.UsageEventDao
import dev.stan.yotsuba.core.database.entity.UsageEventEntity
import dev.stan.yotsuba.core.log.Logs
import dev.stan.yotsuba.data.repository.UsageRecorder
import dev.stan.yotsuba.domain.model.UsageKind
import dev.stan.yotsuba.fake.FakeUsageEventDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageRecorderTest {
    private val sink = Logs.sink
    private val warnings = mutableListOf<String>()

    @After fun tearDown() { Logs.sink = sink }

    @Test fun `records off the caller and reads back as domain events`() = runTest {
        val recorder = UsageRecorder(FakeUsageEventDao(), backgroundScope, StandardTestDispatcher(testScheduler), StandardTestDispatcher(testScheduler))
        recorder.record(UsageKind.IMAGE_SAVED, "g", 1, 42)
        runCurrent()
        val event = recorder.events().first().single()
        assertEquals(UsageKind.IMAGE_SAVED, event.kind)
        assertEquals(42L, event.value)
    }

    @Test fun `a failing insert is logged and swallowed`() = runTest {
        Logs.sink = object : Logs.Sink {
            override fun d(tag: String, msg: String) = Unit
            override fun w(tag: String, msg: String, t: Throwable?) { warnings += msg }
        }
        val broken = object : UsageEventDao {
            override suspend fun insert(event: UsageEventEntity) = throw IllegalStateException("disk full")
            override fun all(): Flow<List<UsageEventEntity>> = throw UnsupportedOperationException()
            override suspend fun deleteKind(kind: String) = throw UnsupportedOperationException()
            override suspend fun deleteAll() = throw UnsupportedOperationException()
            override suspend fun deleteOlderThan(cutoffMs: Long) = throw UnsupportedOperationException()
        }
        val recorder = UsageRecorder(broken, backgroundScope, StandardTestDispatcher(testScheduler), StandardTestDispatcher(testScheduler))
        recorder.record(UsageKind.SEARCH_RUN)
        runCurrent()
        assertTrue(warnings.single().contains("SEARCH_RUN"))
    }
}

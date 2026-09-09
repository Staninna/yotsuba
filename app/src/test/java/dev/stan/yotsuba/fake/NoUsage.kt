package dev.stan.yotsuba.fake

import dev.stan.yotsuba.core.database.dao.UsageEventDao
import dev.stan.yotsuba.core.database.entity.UsageEventEntity
import dev.stan.yotsuba.data.repository.UsageRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Keeps every event in a list; [NoUsage] is the recorder for tests that do not care. */
class FakeUsageEventDao : UsageEventDao {
    val events = mutableListOf<UsageEventEntity>()
    override suspend fun insert(event: UsageEventEntity) { events += event }
    override fun all(): Flow<List<UsageEventEntity>> = flowOf(events.toList())
}

val NoUsage = UsageRecorder(FakeUsageEventDao(), CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)

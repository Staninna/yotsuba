package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.database.dao.UsageEventDao
import dev.stan.yotsuba.core.database.entity.UsageEventEntity
import dev.stan.yotsuba.core.log.Log
import dev.stan.yotsuba.di.ApplicationScope
import dev.stan.yotsuba.di.ComputeDispatcher
import dev.stan.yotsuba.di.IoDispatcher
import dev.stan.yotsuba.domain.model.UsageEvent
import dev.stan.yotsuba.domain.model.UsageKind
import dev.stan.yotsuba.domain.repository.UsageRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The one writer of usage events. Repositories call [record] from wherever the event
 * happens; the insert runs on its own coroutine so the caller's flow never waits on it, and
 * a failed insert is a log line, never an error the user sees.
 */
@Singleton
class UsageRecorder @Inject constructor(
    private val dao: UsageEventDao,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ComputeDispatcher private val compute: CoroutineDispatcher,
) : UsageRepository {

    override fun record(kind: UsageKind, board: String?, threadNo: Long?, value: Long?) {
        val at = System.currentTimeMillis()
        scope.launch(io) {
            try {
                dao.insert(UsageEventEntity(kind = kind.name, board = board, threadNo = threadNo, at = at, value = value))
            } catch (e: Exception) {
                Log.w(TAG, "dropped usage event $kind", e)
            }
        }
    }

    override suspend fun clear(kind: UsageKind) = dao.deleteKind(kind.name)

    override suspend fun clearAll() = dao.deleteAll()

    override suspend fun trim(retainAfterMs: Long) = dao.deleteOlderThan(retainAfterMs)

    override fun events(): Flow<List<UsageEvent>> = dao.all().map { rows ->
        rows.mapNotNull { row ->
            // A kind this build no longer knows is skipped rather than crashing the page.
            val kind = UsageKind.entries.firstOrNull { it.name == row.kind } ?: return@mapNotNull null
            UsageEvent(kind, row.at, row.board, row.threadNo, row.value)
        }
    }.flowOn(compute)

    private companion object {
        const val TAG = "UsageRecorder"
    }
}

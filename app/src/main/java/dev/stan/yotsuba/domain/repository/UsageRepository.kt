package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.domain.model.UsageEvent
import dev.stan.yotsuba.domain.model.UsageKind
import kotlinx.coroutines.flow.Flow

/** Everything recorded so far, oldest first. Writing is the data layer's business. */
interface UsageRepository {
    fun events(): Flow<List<UsageEvent>>

    /** Fire-and-forget; never blocks or throws. */
    fun record(kind: UsageKind, board: String? = null, threadNo: Long? = null, value: Long? = null)
}

package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.domain.model.UsageEvent
import dev.stan.yotsuba.domain.model.UsageKind
import kotlinx.coroutines.flow.Flow

/** Everything recorded so far, oldest first. Writing is the data layer's business. */
interface UsageRepository {
    fun events(): Flow<List<UsageEvent>>

    /** Forgets every event of one kind; the data meter's reset is the one caller. */
    suspend fun clear(kind: UsageKind)
}

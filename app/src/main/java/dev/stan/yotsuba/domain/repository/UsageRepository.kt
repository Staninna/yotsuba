package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.domain.model.UsageEvent
import kotlinx.coroutines.flow.Flow

/** Everything recorded so far, oldest first. Writing is the data layer's business. */
interface UsageRepository {
    fun events(): Flow<List<UsageEvent>>
}

package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.domain.model.UsageEvent
import dev.stan.yotsuba.domain.model.UsageKind
import kotlinx.coroutines.flow.Flow

/**
 * Everything recorded so far, oldest first. Writing is the data layer's business.
 *
 * The log is reading history by another name, so it follows the history controls: the
 * record-history toggle gates what is written, and [trim] and [clearAll] are called from
 * [HistoryRepository] whenever the history table itself is trimmed or cleared.
 */
interface UsageRepository {
    fun events(): Flow<List<UsageEvent>>

    /** Fire-and-forget; never blocks or throws. */
    fun record(kind: UsageKind, board: String? = null, threadNo: Long? = null, value: Long? = null)

    /** Forgets every event of one kind; the data meter's reset is the one caller. */
    suspend fun clear(kind: UsageKind)

    /** Forgets everything, so "Clear history" leaves no record of what was read. */
    suspend fun clearAll()

    /** Forgets events older than [retainAfterMs], so the history retention setting bounds the log. */
    suspend fun trim(retainAfterMs: Long)
}

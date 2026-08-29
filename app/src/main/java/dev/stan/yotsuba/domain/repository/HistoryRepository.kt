package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    val history: Flow<List<HistoryEntry>>
    suspend fun record(entry: HistoryEntry)
    suspend fun updateScrollPosition(board: String, threadNo: Long, postNo: Long)
    suspend fun lastScrollPosition(board: String, threadNo: Long): Long?

    /** Raises the read high-water mark (bottom-most post that has been on screen). */
    suspend fun updateReadUpTo(board: String, threadNo: Long, postNo: Long)
    suspend fun readUpTo(board: String, threadNo: Long): Long?
    suspend fun remove(board: String, threadNo: Long)
    /**
     * Put a removed entry back exactly as it was, read mark and scroll position
     * included. Unlike [record] it never bumps `viewedAt` or trims retention. The default
     * degrades to [record] so fakes that only care about visits keep compiling.
     */
    suspend fun restore(entry: HistoryEntry) = record(entry)
    suspend fun clearAll()
    suspend fun trim(retainAfterMs: Long)
}

package dev.stan.yotsuba.fake

import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * History in a state flow, with the scroll position and read mark of the one thread under
 * test kept as plain fields so a ViewModel test can seed and read them directly.
 */
class FakeHistoryRepository(
    initial: List<HistoryEntry> = emptyList(),
    var savedScrollPostNo: Long? = null,
) : HistoryRepository {
    val state = MutableStateFlow(initial)
    var readMark: Long? = null
    var cleared = false
    override val history: Flow<List<HistoryEntry>> = state

    override suspend fun record(entry: HistoryEntry) {
        // Mirrors the DAO: a visit never carries the read mark with it.
        state.value = listOf(entry.copy(maxReadPostNo = null)) + state.value.filterNot {
            it.board == entry.board && it.threadNo == entry.threadNo
        }
    }
    override suspend fun restore(entry: HistoryEntry) {
        if (state.value.none { it.board == entry.board && it.threadNo == entry.threadNo }) {
            state.value = state.value + entry
        }
    }
    override suspend fun updateScrollPosition(board: String, threadNo: Long, postNo: Long) {
        savedScrollPostNo = postNo
    }
    override suspend fun lastScrollPosition(board: String, threadNo: Long) = savedScrollPostNo
    override suspend fun updateReadUpTo(board: String, threadNo: Long, postNo: Long) { readMark = postNo }
    override suspend fun readUpTo(board: String, threadNo: Long) = readMark
    override suspend fun remove(board: String, threadNo: Long) {
        state.value = state.value.filterNot { it.board == board && it.threadNo == threadNo }
    }
    override suspend fun clearAll() {
        cleared = true
        state.value = emptyList()
    }
    override suspend fun trim(retainAfterMs: Long) {}
}

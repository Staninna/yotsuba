package dev.stan.yotsuba.fake

import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Hidden threads in a state flow a test may also write to directly. */
class FakeHiddenThreadsRepository(initial: List<HiddenThread> = emptyList()) : HiddenThreadsRepository {
    val state = MutableStateFlow(initial)
    override val all: Flow<List<HiddenThread>> = state
    override fun forBoard(board: String) = state.map { list -> list.filter { it.board == board } }
    override suspend fun hide(board: String, threadNo: Long) {
        state.value = state.value + HiddenThread(board, threadNo)
    }
    override suspend fun unhide(board: String, threadNo: Long) {
        state.value = state.value.filterNot { it.board == board && it.threadNo == threadNo }
    }
}

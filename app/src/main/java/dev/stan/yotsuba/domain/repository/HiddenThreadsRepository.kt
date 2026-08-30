package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.domain.model.HiddenThread
import kotlinx.coroutines.flow.Flow

interface HiddenThreadsRepository {
    val all: Flow<List<HiddenThread>>
    fun forBoard(board: String): Flow<List<HiddenThread>>
    suspend fun hide(board: String, threadNo: Long)
    suspend fun unhide(board: String, threadNo: Long)
}

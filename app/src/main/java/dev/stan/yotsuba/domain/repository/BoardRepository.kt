package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.DataResult

interface BoardRepository {
    /** The single place `/f/` is excluded (D13). */
    suspend fun boards(forceRefresh: Boolean = false): DataResult<List<Board>>
    suspend fun board(code: String): Board?
}

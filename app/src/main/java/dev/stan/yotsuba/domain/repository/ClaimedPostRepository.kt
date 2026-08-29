package dev.stan.yotsuba.domain.repository

import kotlinx.coroutines.flow.Flow

/** Posts the user says are theirs. Purely local: the app never posts, so this is the only "(You)". */
interface ClaimedPostRepository {
    fun claimed(board: String, threadNo: Long): Flow<Set<Long>>
    suspend fun claim(board: String, threadNo: Long, postNo: Long)
    suspend fun unclaim(board: String, threadNo: Long, postNo: Long)
}

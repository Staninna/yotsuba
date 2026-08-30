package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository

/**
 * Finds the thread that holds a quoted post which is not on screen: a cross-thread
 * quote, or a deadlink whose post 4chan has since pruned. The sources are asked in the
 * order a reader would want them: what the app already holds, the vault's own copy
 * (works offline, and is what the user chose to keep), 4chan itself, then an archive
 * once 4chan says the thread is gone.
 *
 * A source only counts when it actually contains [postNo]: a partial vault snapshot
 * falls through to the live thread rather than answering "not found" too early.
 */
class GhostResolver(
    private val vault: MediaVaultRepository,
    private val threads: ThreadRepository,
) {
    /**
     * @param held a copy of the thread already in memory, if any; consulted first.
     * @param skipLive a deadlink: 4chan has already said the post is gone, so asking
     *   again is a wasted request and the chain goes straight from the vault to the archive.
     */
    suspend fun resolve(
        board: String,
        threadNo: Long,
        postNo: Long,
        held: ThreadDetails? = null,
        skipLive: Boolean = false,
    ): DataResult<ThreadDetails> {
        if (held != null && held.has(postNo)) return DataResult.Success(held)
        vault.savedThread(board, threadNo)?.takeIf { it.has(postNo) }?.let {
            return DataResult.Success(it.copy(offlineCopy = true))
        }
        if (!skipLive) {
            when (val live = threads.thread(board, threadNo)) {
                is DataResult.Success -> if (live.value.has(postNo)) return live
                is DataResult.Failure -> if (live.error != NetworkError.NotFound) return live
            }
        }
        val archived = threads.archivedThread(board, threadNo)
        return when {
            archived is DataResult.Success && archived.value.has(postNo) -> archived
            archived is DataResult.Failure && archived.error != NetworkError.NotFound -> archived
            else -> DataResult.Failure(NetworkError.NotFound)
        }
    }

    private fun ThreadDetails.has(postNo: Long): Boolean = posts.any { it.no == postNo }
}

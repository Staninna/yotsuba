package dev.stan.yotsuba.fake

import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A [FakeMediaVault] that records what was asked of it. Saves and deletes are lists in call
 * order; `statuses` is too transient to assert on. [paths] backs [saved] so a test can mark
 * a URL as already on disk.
 */
class FakeVault(access: Boolean = true) : FakeMediaVault() {
    val access = MutableStateFlow(access)
    val saves = mutableListOf<Pair<MediaItem, VaultSaveContext>>()
    val deleted = mutableListOf<String>()
    val paths = MutableStateFlow(emptyMap<String, String?>())
    /** Answer for [savedThread] on any thread not in [savedThreads]. */
    var snapshot: ThreadDetails? = null
    /** Saved copies of other threads, by (board, no); [snapshot] answers for the rest. */
    val savedThreads = mutableMapOf<Pair<String, Long>, ThreadDetails>()
    /** Every [snapshotThread] call, in order. */
    val snapshotCalls = mutableListOf<Pair<String, Long>>()
    var snapshotError: VaultError? = null
    /** Holds [snapshotThread] while set, so a test can observe the in-flight state. */
    var gate: CompletableDeferred<Unit>? = null

    override fun hasStorageAccess() = access.value
    override val storageAccess: Flow<Boolean> get() = access
    override fun saved(): Flow<Map<String, String?>> = paths
    override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? {
        saves += item to context
        return null
    }
    override suspend fun delete(url: String): VaultError? {
        deleted += url
        return null
    }
    override suspend fun savedThread(board: String, threadNo: Long): ThreadDetails? =
        savedThreads[board to threadNo] ?: snapshot
    override suspend fun snapshotThread(board: String, threadNo: Long): VaultError? {
        snapshotCalls += board to threadNo
        gate?.await()
        return snapshotError
    }
}

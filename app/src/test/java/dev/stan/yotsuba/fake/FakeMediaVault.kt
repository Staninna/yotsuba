package dev.stan.yotsuba.fake

import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A vault with storage access, nothing saved and every operation a successful no-op.
 * Test fakes extend it and override only what the test observes; the interface itself
 * carries no defaults, so an implementation cannot inherit a silently wrong one.
 */
open class FakeMediaVault : MediaVaultRepository {
    override fun hasStorageAccess(): Boolean = true
    override suspend fun unindexedThreadCount(): Int = 0
    override val storageAccess: Flow<Boolean> get() = flowOf(hasStorageAccess())
    override fun refreshStorageAccess() {}
    override fun entries(): Flow<List<VaultEntry>> = flowOf(emptyList())
    override fun saved(): Flow<Map<String, String?>> = flowOf(emptyMap())
    override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? = null
    override suspend fun delete(url: String): VaultError? = null
    override suspend fun trash(url: String): VaultError? = delete(url)
    override val trashed: Flow<List<VaultEntry>> = flowOf(emptyList())
    override suspend fun restoreTrashed(url: String): VaultError? = VaultError.NotFound
    override suspend fun emptyTrash() {}
    override suspend fun purgeExpiredTrash() {}
    override suspend fun exportToGallery(url: String): VaultError? = null
    override suspend fun savedThread(board: String, threadNo: Long): ThreadDetails? = null
    override suspend fun importLocalThread(name: String, sources: List<ImportSource>): VaultError? = null
    override suspend fun syncSavedThreads(onProgress: (Int, Int) -> Unit, skip: Set<VaultLocation>) = VaultSyncSummary()
    override suspend fun snapshotThread(board: String, threadNo: Long): VaultError? = null
    override suspend fun snapshotThreads(targets: List<VaultLocation>, onProgress: (Int, Int) -> Unit) = VaultSyncSummary()
    override suspend fun renameThread(board: String, threadNo: Long, name: String): VaultError? = null
    override suspend fun mergeThreads(fromBoard: String, fromThreadNo: Long, intoBoard: String, intoThreadNo: Long): VaultError? = null
    override suspend fun rescan() {}
    override suspend fun migrateLegacyIfNeeded() {}
}

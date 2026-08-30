package dev.stan.yotsuba.fake

import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * An inert vault: storage granted, nothing saved, every operation succeeds and records
 * nothing. Tests subclass it and override only the members they script. The interface's
 * own defaults (trash, restoreTrashed, snapshot, rename, merge, ...) are left alone.
 */
open class FakeVault(private val entries: List<VaultEntry> = emptyList()) : MediaVaultRepository {
    var access = true

    override fun hasStorageAccess() = access
    override fun entries(): Flow<List<VaultEntry>> = flowOf(entries)
    override fun saved(): Flow<Map<String, String?>> = flowOf(emptyMap())
    override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? = null
    override suspend fun delete(url: String): VaultError? = null
    override suspend fun savedThread(board: String, threadNo: Long): ThreadDetails? = null
    override suspend fun importLocalThread(name: String, sources: List<ImportSource>): VaultError? = null
    override suspend fun syncSavedThreads(onProgress: (Int, Int) -> Unit) = VaultSyncSummary()
    override suspend fun rescan() {}
    override suspend fun migrateLegacyIfNeeded() {}
}

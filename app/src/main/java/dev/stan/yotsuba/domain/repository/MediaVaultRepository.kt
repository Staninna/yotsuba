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

interface MediaVaultRepository {
    /** True when the app may read/write the vault directory. */
    fun hasStorageAccess(): Boolean

    /** Entries with a real file on disk, newest first. */
    fun entries(): Flow<List<VaultEntry>>

    /** Every saved URL, including legacy rows whose file was never located. */
    fun savedUrls(): Flow<Set<String>>

    /** URL → absolute path on disk, for buffer-free playback of already-saved media. */
    fun savedPaths(): Flow<Map<String, String>>

    /** Streams the media to its structured vault location, updates meta.json and the DB. */
    suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError?

    /** Deletes the file, its meta entry, and the DB row; prunes emptied thread directories. */
    suspend fun delete(url: String): VaultError?

    /**
     * The thread as it was saved, rebuilt from its sidecars — posts, quote graph and all.
     * Null when nothing was captured for it. `archived` and `closed` are unknowable from
     * disk and come back false, so nothing may present them as fact.
     */
    suspend fun savedThread(board: String, threadNo: Long): ThreadDetails?

    /**
     * Copies [sources] into a new thread under the reserved local board, so a folder of
     * the user's own images browses exactly like a saved 4chan thread. Files are copied,
     * never referenced: a picker grant can be revoked, and the vault must outlive it.
     */
    suspend fun importLocalThread(name: String, sources: List<ImportSource>): VaultError?

    /**
     * Refreshes each saved thread's captured comment section from the live thread, while
     * it still exists. A thread that has 404'd keeps whatever was captured before.
     *
     * Network-bound and rate-limited to roughly one thread per second, so [onProgress]
     * reports `(done, total)` for a caller that needs to show it going.
     */
    suspend fun syncSavedThreads(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): VaultSyncSummary

    /** Rebuilds the saved-media DB purely from the meta.json sidecars on disk. */
    suspend fun rescan()

    /** One-time move of the legacy flat Pictures/Yotsuba files into the vault. No-op once done. */
    suspend fun migrateLegacyIfNeeded()
}

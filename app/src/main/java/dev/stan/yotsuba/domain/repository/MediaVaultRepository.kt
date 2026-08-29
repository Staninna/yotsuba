package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

interface MediaVaultRepository {
    /** True when the app may read/write the vault directory. */
    fun hasStorageAccess(): Boolean

    /**
     * [hasStorageAccess] as something a screen can observe. The grant happens in a system
     * settings page the app never sees, so the value is re-read on [refreshStorageAccess],
     * which a screen calls when it resumes.
     */
    val storageAccess: Flow<Boolean> get() = flow { emit(hasStorageAccess()) }

    fun refreshStorageAccess() {}

    /** Entries with a real file on disk, newest first. */
    fun entries(): Flow<List<VaultEntry>>

    /**
     * Every saved URL mapped to its file on disk, or to null for a legacy row whose file was
     * never located. One query answers both "is it saved" and "where is it".
     *
     * The default exists only so fakes that predate it keep compiling; real
     * implementations override it and build [savedUrls] and [savedPaths] on top.
     */
    fun saved(): Flow<Map<String, String?>> = combine(savedUrls(), savedPaths()) { urls, paths ->
        urls.associateWith { paths[it] }
    }

    /** Every saved URL, including legacy rows whose file was never located. */
    @Deprecated("Use saved().keys", ReplaceWith("saved().map { it.keys }"))
    fun savedUrls(): Flow<Set<String>>

    /** URL → absolute path on disk, for buffer-free playback of already-saved media. */
    @Deprecated("Use saved() and drop the null paths", ReplaceWith("saved().map { it.filterValues { p -> p != null } }"))
    fun savedPaths(): Flow<Map<String, String>>

    /** Streams the media to its structured vault location, updates meta.json and the DB. */
    suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError?

    /** Deletes the file, its meta entry, and the DB row; prunes emptied thread directories. */
    suspend fun delete(url: String): VaultError?

    /**
     * Like [delete] as far as the index is concerned, but the file is moved aside rather
     * than removed, so [restoreTrashed] can bring it back until [purgeTrash] runs. The
     * default cannot undo; real implementations override it.
     */
    suspend fun trash(url: String): VaultError? = delete(url)

    /** Puts a [trash]ed file back where it was, sidecar entry and row included. */
    suspend fun restoreTrashed(url: String): VaultError? = VaultError.NotFound

    /** Empties the trash for good. Called at launch and once an undo window closes. */
    suspend fun purgeTrash() {}

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

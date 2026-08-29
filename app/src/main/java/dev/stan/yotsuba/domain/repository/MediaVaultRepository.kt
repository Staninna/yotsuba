package dev.stan.yotsuba.domain.repository

import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import kotlinx.coroutines.flow.Flow
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
     */
    fun saved(): Flow<Map<String, String?>>

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
     * Copies a saved file into the device gallery (MediaStore), so it shows up in other
     * apps; the vault's own copy stays where it is. The default cannot.
     */
    suspend fun exportToGallery(url: String): VaultError? = VaultError.Io("not supported")

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

    /**
     * [syncSavedThreads], leaving out [skip]: a caller that has just snapshotted those
     * threads has no reason to fetch them twice.
     */
    suspend fun syncSavedThreads(
        onProgress: (done: Int, total: Int) -> Unit,
        skip: Set<VaultLocation>,
    ): VaultSyncSummary = syncSavedThreads(onProgress)

    /**
     * Captures the whole live thread into its vault sidecars without saving any media --
     * the bookmark case: watched, not yet saved from. Merges into an existing directory
     * or creates one carrying only `meta.json` and `posts.json`. [VaultError.NotFound]
     * when the thread is gone.
     */
    suspend fun snapshotThread(board: String, threadNo: Long): VaultError? = VaultError.Io("not supported")

    /**
     * [snapshotThread] over many, paced like [syncSavedThreads] and stopping the same way
     * on a rate limit. Threads that turned out gone count as [VaultSyncSummary.gone].
     */
    suspend fun snapshotThreads(
        targets: List<VaultLocation>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): VaultSyncSummary = VaultSyncSummary()

    /**
     * Gives an imported thread a new subject: the sidecar and the directory name both
     * change. Only local threads can be renamed; a saved 4chan thread keeps its own.
     */
    suspend fun renameThread(board: String, threadNo: Long, name: String): VaultError? = VaultError.Io("not supported")

    /**
     * Moves every file and its sidecar entry from one thread into another, then drops the
     * emptied directory and rebuilds the index. The two must share a board.
     */
    suspend fun mergeThreads(
        fromBoard: String, fromThreadNo: Long, intoBoard: String, intoThreadNo: Long,
    ): VaultError? = VaultError.Io("not supported")

    /** Rebuilds the saved-media DB purely from the meta.json sidecars on disk. */
    suspend fun rescan()

    /** One-time move of the legacy flat Pictures/Yotsuba files into the vault. No-op once done. */
    suspend fun migrateLegacyIfNeeded()
}

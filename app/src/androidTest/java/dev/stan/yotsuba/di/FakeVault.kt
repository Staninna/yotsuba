package dev.stan.yotsuba.di

import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.DedupMode
import dev.stan.yotsuba.domain.model.DuplicateGroup
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.domain.model.VaultPaths
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.domain.repository.BackupInfo
import dev.stan.yotsuba.domain.repository.BackupRepository
import dev.stan.yotsuba.domain.repository.BackupResult
import dev.stan.yotsuba.domain.repository.DirectUploadEngine
import dev.stan.yotsuba.domain.repository.HostedFile
import dev.stan.yotsuba.domain.repository.MediaSaveQueue
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.ReverseSearchRepository
import dev.stan.yotsuba.domain.repository.TemporaryHost
import dev.stan.yotsuba.domain.repository.VaultDedupRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/*
 * The vault and everything that writes into or reads out of it. Nothing here touches the
 * disk: a save lands in a list, a delete moves it to the trash list, and every mutation
 * is recorded so a flow test can assert what the screen asked for, not only what it shows.
 */

/**
 * An in-memory vault. Every save lands in [entries] (and [saves], in call order); [trash]
 * moves an entry to [trashed] and [restoreTrashed] brings it back; rename and merge rewrite
 * the entries they touch. [savedThread] rebuilds a thread from the posts saved into it, so a
 * test can drive the offline fallback by saving first.
 */
@Singleton
class FakeMediaVaultRepository @Inject constructor() : MediaVaultRepository {
    val state = MutableStateFlow<List<VaultEntry>>(emptyList())
    val trashState = MutableStateFlow<List<VaultEntry>>(emptyList())
    val access = MutableStateFlow(true)

    /** Every successful save, in call order, with the context the screen passed. */
    val saves = mutableListOf<Pair<MediaItem, VaultSaveContext>>()
    val snapshotCalls = mutableListOf<VaultLocation>()
    val snapshotBatchCalls = mutableListOf<List<VaultLocation>>()
    val exported = mutableListOf<String>()
    val renames = mutableListOf<Pair<VaultLocation, String>>()
    val merges = mutableListOf<Pair<VaultLocation, VaultLocation>>()
    val imports = mutableListOf<Pair<String, List<ImportSource>>>()
    var rescanCalls = 0
    var syncCalls = 0
    var emptyTrashCalls = 0

    /** Knobs. A non-null error makes the matching operation fail with it. */
    var saveError: VaultError? = null
    var deleteError: VaultError? = null
    var exportError: VaultError? = null
    var importError: VaultError? = null
    var renameError: VaultError? = null
    var mergeError: VaultError? = null
    var snapshotError: VaultError? = null
    var syncSummary = VaultSyncSummary()
    var syncProgressSteps = 0
    var unindexedCount = 0
    /** Sidecar-only threads: what [savedThread] answers when nothing was saved through the UI. */
    val sidecars = mutableMapOf<Pair<String, Long>, ThreadDetails>()

    val entriesNow: List<VaultEntry> get() = state.value

    fun seed(vararg items: VaultEntry) {
        state.value = items.toList()
    }

    override fun hasStorageAccess(): Boolean = access.value
    override val storageAccess: Flow<Boolean> = access
    override fun refreshStorageAccess() = Unit
    override fun entries(): Flow<List<VaultEntry>> = state
    override fun saved(): Flow<Map<String, String?>> = state.map { list -> list.associate { it.url to it.absolutePath } }

    override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? = saveNow(item, context)

    /** Mirrors production: a successful save lands in the entries flow, so badges flip to SAVED. */
    fun saveNow(item: MediaItem, context: VaultSaveContext): VaultError? {
        saveError?.let { return it }
        val entry = TestSeed.vaultEntry(item, context.board, context.threadNo, context.threadSubject)
        state.update { list -> list.filterNot { it.url == entry.url } + entry }
        saves += item to context
        return null
    }

    override suspend fun delete(url: String): VaultError? {
        deleteError?.let { return it }
        if (state.value.none { it.url == url }) return VaultError.NotFound
        state.update { list -> list.filterNot { it.url == url } }
        return null
    }

    override suspend fun trash(url: String): VaultError? {
        deleteError?.let { return it }
        val entry = state.value.firstOrNull { it.url == url } ?: return VaultError.NotFound
        state.update { list -> list - entry }
        trashState.update { it + entry }
        return null
    }

    override val trashed: Flow<List<VaultEntry>> = trashState

    override suspend fun restoreTrashed(url: String): VaultError? {
        val entry = trashState.value.firstOrNull { it.url == url } ?: return VaultError.NotFound
        trashState.update { list -> list - entry }
        state.update { it + entry }
        return null
    }

    override suspend fun emptyTrash() {
        emptyTrashCalls++
        trashState.value = emptyList()
    }

    override suspend fun purgeExpiredTrash() = Unit

    override suspend fun exportToGallery(url: String): VaultError? {
        exportError?.let { return it }
        exported += url
        return null
    }

    override suspend fun syncSavedThreads(onProgress: (Int, Int) -> Unit, skip: Set<VaultLocation>): VaultSyncSummary {
        syncCalls++
        repeat(syncProgressSteps) { onProgress(it + 1, syncProgressSteps) }
        return syncSummary
    }

    override suspend fun snapshotThread(board: String, threadNo: Long): VaultError? {
        snapshotCalls += VaultLocation(board, threadNo)
        return snapshotError
    }

    override suspend fun snapshotThreads(targets: List<VaultLocation>, onProgress: (Int, Int) -> Unit): VaultSyncSummary {
        snapshotBatchCalls += targets
        return syncSummary
    }

    override suspend fun renameThread(board: String, threadNo: Long, name: String): VaultError? {
        renameError?.let { return it }
        renames += VaultLocation(board, threadNo) to name
        state.update { list ->
            list.map { if (it.location.board == board && it.location.threadNo == threadNo) it.copy(subject = name) else it }
        }
        return null
    }

    override suspend fun mergeThreads(fromBoard: String, fromThreadNo: Long, intoBoard: String, intoThreadNo: Long): VaultError? {
        mergeError?.let { return it }
        merges += VaultLocation(fromBoard, fromThreadNo) to VaultLocation(intoBoard, intoThreadNo)
        val target = state.value.firstOrNull { it.location.board == intoBoard && it.location.threadNo == intoThreadNo }
        state.update { list ->
            list.map {
                if (it.location.board == fromBoard && it.location.threadNo == fromThreadNo) {
                    it.copy(location = VaultLocation(intoBoard, intoThreadNo), subject = target?.subject ?: it.subject)
                } else it
            }
        }
        return null
    }

    override suspend fun importLocalThread(name: String, sources: List<ImportSource>): VaultError? {
        importError?.let { return it }
        imports += name to sources
        val threadNo = 100L + imports.size
        state.update { list ->
            list + sources.mapIndexed { i, source ->
                val ext = VaultPaths.extensionOf(source.displayName)
                VaultEntry(
                    url = "file://local/$threadNo/${source.displayName}",
                    location = VaultLocation(VaultPaths.LOCAL_BOARD_NAME, threadNo),
                    subject = name,
                    postNo = null,
                    displayName = source.displayName,
                    absolutePath = "/fake-vault/_local/$threadNo/${source.displayName}",
                    ext = ext,
                    sizeBytes = 1_000L * (i + 1),
                    width = 100,
                    height = 100,
                    thumbnailUrl = null,
                    savedAt = 1_700_000_300L + i,
                )
            }
        }
        return null
    }

    /** The posts saved into this thread (each save's own post plus its conversation), else the seeded sidecar. */
    override suspend fun savedThread(board: String, threadNo: Long): ThreadDetails? {
        val here = saves.filter { (_, ctx) -> ctx.board == board && ctx.threadNo == threadNo }
        if (here.isEmpty()) return sidecars[board to threadNo]
        val posts = here.flatMap { (_, ctx) -> listOfNotNull(ctx.post) + ctx.conversation }
            .distinctBy { it.no }.sortedBy { it.no }
        return ThreadDetails(board, threadNo, posts, archived = false, closed = false, backlinks = emptyMap(), offlineCopy = true)
    }

    override suspend fun rescan() {
        rescanCalls++
        unindexedCount = 0
    }

    override suspend fun unindexedThreadCount(): Int = unindexedCount
    override suspend fun migrateLegacyIfNeeded() = Unit
}

/**
 * Saves straight through to the vault fake on the caller's thread, so a badge flips to
 * SAVED before the test's next assertion without any waiting. [holdNext] parks the next
 * enqueue as Queued instead, for the in-progress affordances; [failNextWith] fails it.
 */
@Singleton
class FakeMediaSaveQueue @Inject constructor(
    private val vault: FakeMediaVaultRepository,
) : MediaSaveQueue {
    private val pending = MutableStateFlow<Map<String, MediaSaveStatus>>(emptyMap())
    private val contexts = mutableMapOf<String, Pair<MediaItem, VaultSaveContext>>()
    var failNextWith: VaultError? = null
    var holdNext = false
    val cancelled = mutableListOf<String>()
    val retried = mutableListOf<String>()

    override val statuses: Flow<Map<String, MediaSaveStatus>> = combine(vault.saved(), pending) { saved, pending ->
        pending + saved.keys.associateWith { MediaSaveStatus.Saved }
    }

    override fun enqueue(item: MediaItem, context: VaultSaveContext) {
        contexts[item.fullUrl] = item to context
        val fail = failNextWith
        when {
            fail != null -> {
                failNextWith = null
                pending.update { it + (item.fullUrl to MediaSaveStatus.Failed(fail)) }
            }
            holdNext -> {
                holdNext = false
                pending.update { it + (item.fullUrl to MediaSaveStatus.Queued) }
            }
            else -> {
                val error = vault.saveNow(item, context)
                pending.update { if (error == null) it - item.fullUrl else it + (item.fullUrl to MediaSaveStatus.Failed(error)) }
            }
        }
    }

    /** Lets a held save through, as if the download finished. */
    fun completeHeld(url: String) {
        val (item, context) = contexts[url] ?: return
        pending.update { it - url }
        vault.saveNow(item, context)
    }

    override fun cancel(url: String) {
        cancelled += url
        pending.update { it - url }
    }

    override fun retry(url: String) {
        retried += url
        val (item, context) = contexts[url] ?: return
        pending.update { it - url }
        enqueue(item, context)
    }

    override fun dismiss(url: String) = pending.update { it - url }
}

@Singleton
class FakeVaultDedupRepository @Inject constructor() : VaultDedupRepository {
    val groups = mutableListOf<DuplicateGroup>()
    var missingHashes = 0
    var backfillCalls = 0
    val findCalls = mutableListOf<Pair<DedupMode, Int>>()

    override suspend fun findByMd5(md5: String): String? = null
    override suspend fun recordMd5(url: String, md5: String) = Unit
    override suspend fun missingHashCount(): Int = missingHashes

    override suspend fun backfillHashes(onProgress: (Int, Int) -> Unit) {
        backfillCalls++
        val total = missingHashes
        repeat(total) { onProgress(it + 1, total) }
        missingHashes = 0
    }

    override suspend fun findDuplicates(mode: DedupMode, maxDistance: Int): List<DuplicateGroup> {
        findCalls += mode to maxDistance
        return groups.toList()
    }
}

@Singleton
class FakeBackupRepository @Inject constructor() : BackupRepository {
    var available: BackupInfo? = null
    var freshInstall = false
    var exportResult: BackupResult = BackupResult.Exported(1_700_000_000_000L)
    var importResult: BackupResult = BackupResult.Imported(bookmarks = 2, hiddenThreads = 1)
    var exportCalls = 0
    var importCalls = 0

    override suspend fun export(): BackupResult {
        exportCalls++
        return exportResult
    }

    override suspend fun import(): BackupResult {
        importCalls++
        return importResult
    }

    override suspend fun available(): BackupInfo? = available
    override suspend fun isFreshInstall(): Boolean = freshInstall
}

@Singleton
class FakeReverseSearchRepository @Inject constructor() : ReverseSearchRepository {
    var directResult: DataResult<String> = DataResult.Success("https://example.invalid/results")
    var hostResult: DataResult<HostedFile> = DataResult.Success(HostedFile("https://example.invalid/hosted.png", TemporaryHost.LITTERBOX))
    val directCalls = mutableListOf<DirectUploadEngine>()
    var hostCalls = 0

    override suspend fun directSearchUrl(engine: DirectUploadEngine, file: File, ext: String): DataResult<String> {
        directCalls += engine
        return directResult
    }

    override suspend fun hostTemporarily(file: File, ext: String): DataResult<HostedFile> {
        hostCalls++
        return hostResult
    }

    fun failAll() {
        directResult = DataResult.Failure(NetworkError.Server(500))
        hostResult = DataResult.Failure(NetworkError.Server(500))
    }
}

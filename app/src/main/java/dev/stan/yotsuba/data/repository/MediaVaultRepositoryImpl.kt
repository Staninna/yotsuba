package dev.stan.yotsuba.data.repository

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.stan.yotsuba.core.database.dao.SavedMediaDao
import dev.stan.yotsuba.core.media.GalleryExporter
import dev.stan.yotsuba.core.media.MediaByteSource
import dev.stan.yotsuba.core.media.mimeOf
import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.core.vault.VaultThreadMeta
import dev.stan.yotsuba.core.vault.VideoStills
import dev.stan.yotsuba.data.repository.toThreadPost
import dev.stan.yotsuba.data.repository.toVaultMeta
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.domain.model.VaultPaths
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.domain.model.isVideoExt
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val VAULT_MIGRATED = booleanPreferencesKey("vault_legacy_migrated_v1")

@Singleton
class MediaVaultRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedMediaDao: SavedMediaDao,
    private val store: VaultStore,
    private val migration: VaultLegacyMigration,
    private val vaultTrash: VaultTrash,
    private val localImporter: LocalThreadImporter,
    private val galleryExporter: GalleryExporter,
    private val byteSource: MediaByteSource,
    private val threadRepository: ThreadRepository,
    private val preferences: DataStore<Preferences>,
    private val settings: SettingsRepository,
) : MediaVaultRepository {

    override fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    // Starts false and is filled in on the first refresh: the check itself needs a real
    // Android runtime, and this singleton is built at process start (and under Robolectric).
    private val storageAccessState = MutableStateFlow(false)
    override val storageAccess: Flow<Boolean> = storageAccessState.onStart { refreshStorageAccess() }

    override fun refreshStorageAccess() {
        storageAccessState.value = runCatching { hasStorageAccess() }.getOrDefault(false)
    }

    override fun entries(): Flow<List<VaultEntry>> = savedMediaDao.all().map { rows ->
        rows.filter { it.absolutePath.isNotEmpty() }.map { it.toVaultEntry() }
    }

    override fun saved(): Flow<Map<String, String?>> = savedMediaDao.all().map { rows ->
        rows.associate { it.url to it.absolutePath.ifEmpty { null } }
    }

    override suspend fun save(item: MediaItem, saveContext: VaultSaveContext): VaultError? =
        withContext(Dispatchers.IO) {
            if (!hasStorageAccess()) return@withContext VaultError.NoAccess
            attempt {
                store.ensureRoot()
                val dir = store.threadDirFor(
                    saveContext.board, saveContext.threadNo, saveContext.threadSubject, saveContext.opExcerpt,
                ).apply { mkdirs() }

                val target = store.uniqueFile(
                    dir, VaultPaths.fileName(item.postNo, item.filename, item.ext),
                )
                streamTo(item.fullUrl, target)
                val still = VideoStills.captureIfVideo(target)

                val savedAt = System.currentTimeMillis()
                val row = store.lock.withLock {
                    val row = store.recordSavedFile(
                        dir, saveContext.board, saveContext.threadNo, saveContext.threadSubject,
                        target, item, saveContext.post, savedAt, still,
                    )
                    // Same lock as meta.json. The two writes are not one transaction, but
                    // each is atomic and a missing posts.json already means "no snapshot",
                    // so a crash between them degrades to exactly the pre-existing state.
                    store.updatePosts(
                        dir = dir,
                        board = saveContext.board,
                        threadNo = saveContext.threadNo,
                        incoming = saveContext.conversation.map { it.toVaultMeta() },
                    )
                    row
                }
                savedMediaDao.insert(row)
            }
        }

    override suspend fun savedThread(board: String, threadNo: Long): ThreadDetails? =
        withContext(Dispatchers.IO) {
            if (!hasStorageAccess()) return@withContext null
            val dir = store.threadDir(board, threadNo) ?: return@withContext null
            val saved = store.readPosts(dir)?.takeIf { it.posts.isNotEmpty() } ?: return@withContext null
            buildThreadDetails(
                board = saved.board.ifEmpty { board },
                threadNo = saved.threadNo,
                posts = saved.posts.map { it.toThreadPost(saved.board.ifEmpty { board }) },
            )
        }

    override suspend fun delete(url: String): VaultError? = withContext(Dispatchers.IO) {
        val entity = savedMediaDao.byUrl(url) ?: return@withContext VaultError.NotFound
        attempt {
            if (entity.absolutePath.isNotEmpty()) {
                val file = File(entity.absolutePath)
                val dir = file.parentFile
                file.delete()
                // Images have no still; a missing one is nothing to report.
                VideoStills.stillFor(file).delete()
                if (dir != null) {
                    store.lock.withLock {
                        store.updateMeta(dir) { it.remove(file.name) }
                        store.pruneIfEmpty(dir)
                    }
                }
            }
            savedMediaDao.delete(url)
        }
    }

    override suspend fun trash(url: String): VaultError? = withContext(Dispatchers.IO) {
        val entity = savedMediaDao.byUrl(url) ?: return@withContext VaultError.NotFound
        if (entity.absolutePath.isEmpty()) return@withContext delete(url)
        vaultTrash.trash(entity)
    }

    override val trashed: Flow<List<VaultEntry>> = vaultTrash.entries.map { rows -> rows.map { it.toVaultEntry() } }

    override suspend fun restoreTrashed(url: String): VaultError? = withContext(Dispatchers.IO) {
        vaultTrash.restore(url)
    }

    override suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        if (!hasStorageAccess()) return@withContext
        vaultTrash.empty()
    }

    override suspend fun purgeExpiredTrash() = withContext(Dispatchers.IO) {
        if (!hasStorageAccess()) return@withContext
        vaultTrash.purgeExpired()
    }

    override suspend fun exportToGallery(url: String): VaultError? = withContext(Dispatchers.IO) {
        val entity = savedMediaDao.byUrl(url) ?: return@withContext VaultError.NotFound
        val file = File(entity.absolutePath).takeIf { it.isFile } ?: return@withContext VaultError.NotFound
        attempt { galleryExporter.export(file, mimeOf(entity.ext.orEmpty())) }
    }

    override suspend fun importLocalThread(
        name: String,
        sources: List<ImportSource>,
    ): VaultError? = withContext(Dispatchers.IO) {
        if (!hasStorageAccess()) return@withContext VaultError.NoAccess
        if (sources.isEmpty()) return@withContext null
        localImporter.import(name, sources)
    }

    override suspend fun syncSavedThreads(
        onProgress: (done: Int, total: Int) -> Unit,
        skip: Set<VaultLocation>,
    ): VaultSyncSummary = withContext(Dispatchers.IO) {
        if (!hasStorageAccess() || !store.root.isDirectory) return@withContext VaultSyncSummary()
        val targets = savedThreads().filterNot { it in skip }
        runPass(targets, onProgress) { location, thread ->
            val dir = store.threadDir(location.board, location.threadNo) ?: return@runPass
            // The whole comment section rather than the conversation around what was
            // saved, because while the thread is alive this is the only chance to take it.
            store.updatePosts(
                dir = dir,
                board = location.board,
                threadNo = location.threadNo,
                incoming = thread.posts.map { it.toVaultMeta() },
            )
        }
    }

    override suspend fun snapshotThread(board: String, threadNo: Long): VaultError? =
        withContext(Dispatchers.IO) {
            if (!hasStorageAccess()) return@withContext VaultError.NoAccess
            when (val result = threadRepository.thread(board, threadNo, forceRefresh = true)) {
                is DataResult.Success -> attempt {
                    store.ensureRoot()
                    store.lock.withLock { writeSnapshot(board, threadNo, result.value) }
                }
                is DataResult.Failure -> when (result.error) {
                    NetworkError.NotFound -> {
                        store.lock.withLock { pruneIfDead(board, threadNo) }
                        VaultError.NotFound
                    }
                    else -> VaultError.Io(result.error.toString())
                }
            }
        }

    override suspend fun snapshotThreads(
        targets: List<VaultLocation>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): VaultSyncSummary = withContext(Dispatchers.IO) {
        if (!hasStorageAccess()) return@withContext VaultSyncSummary()
        store.ensureRoot()
        runPass(targets.filter { it.isRemote }, onProgress) { location, thread ->
            writeSnapshot(location.board, location.threadNo, thread)
        }
    }

    /** Under the store lock. */
    private fun writeSnapshot(board: String, threadNo: Long, thread: ThreadDetails) {
        val op = thread.posts.firstOrNull { it.isOp } ?: thread.posts.firstOrNull()
        store.snapshot(
            board = board,
            threadNo = threadNo,
            subject = op?.subject,
            opExcerpt = op?.body?.plainText?.take(60),
            posts = thread.posts.map { it.toVaultMeta() },
        )
    }

    /**
     * One rate-limited walk over [targets]: fetch each live thread and hand it to [apply]
     * under the store lock. A 404 is the thread's end, and the moment its sidecar may be
     * compacted; a rate limit ends the pass.
     */
    private suspend fun runPass(
        targets: List<VaultLocation>,
        onProgress: (done: Int, total: Int) -> Unit,
        apply: (VaultLocation, ThreadDetails) -> Unit,
    ): VaultSyncSummary {
        onProgress(0, targets.size)
        var updated = 0
        var gone = 0
        var failed = 0
        var pruned = 0
        var rateLimited = false
        val touched = mutableSetOf<VaultLocation>()

        for ((index, target) in targets.withIndex()) {
            when (val result = threadRepository.thread(target.board, target.threadNo, forceRefresh = true)) {
                is DataResult.Success -> {
                    val outcome = attempt { store.lock.withLock { apply(target, result.value) } }
                    if (outcome == null) {
                        updated++
                        touched += target
                    } else {
                        failed++
                    }
                }
                is DataResult.Failure -> when (result.error) {
                    // Already gone. Whatever was captured before is all there will ever be,
                    // so this is the one moment the sidecar can be compacted.
                    NetworkError.NotFound -> {
                        gone++
                        touched += target
                        if (store.lock.withLock { pruneIfDead(target.board, target.threadNo) }) pruned++
                    }
                    // Backing off is the whole point of a rate limit; finish another day.
                    NetworkError.RateLimited -> {
                        rateLimited = true
                    }
                    else -> failed++
                }
            }
            onProgress(index + 1, targets.size)
            if (rateLimited) break
        }
        return VaultSyncSummary(
            updated = updated, gone = gone, failed = failed, pruned = pruned, rateLimited = rateLimited,
            touched = touched,
        )
    }

    /** Under the store lock. True when the thread's sidecar was compacted this time. */
    private suspend fun pruneIfDead(board: String, threadNo: Long): Boolean {
        if (!settings.settings.first().pruneDeadSidecars) return false
        val dir = store.threadDir(board, threadNo) ?: return false
        return runCatching { store.pruneDeadThread(dir) }.getOrNull() != null
    }

    /** Saved threads that have an upstream to sync against; local imports and pruned threads do not. */
    private fun savedThreads(): List<VaultLocation> =
        store.threadMetas().mapNotNull { (_, meta) ->
            if (meta.isPruned) return@mapNotNull null
            val threadNo = meta.threadNo ?: return@mapNotNull null
            val board = meta.board.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            VaultLocation(board, threadNo).takeIf { it.isRemote }
        }

    override suspend fun renameThread(board: String, threadNo: Long, name: String): VaultError? =
        withContext(Dispatchers.IO) {
            if (!hasStorageAccess()) return@withContext VaultError.NoAccess
            if (!VaultLocation(board, threadNo).isLocal) return@withContext VaultError.Io("only imported threads can be renamed")
            val dir = store.threadDir(board, threadNo) ?: return@withContext VaultError.NotFound
            val trimmed = name.trim().ifEmpty { return@withContext VaultError.Io("empty name") }
            attempt {
                store.lock.withLock {
                    store.updateMeta(dir) { it.copy(subject = trimmed) }
                    store.updatePosts(
                        dir, board, threadNo,
                        // Only the OP carries the subject; a merge into an empty posts.json is a no-op.
                        store.readPosts(dir)?.posts?.filter { it.isOp }?.map { it.copy(subject = trimmed) }.orEmpty(),
                    )
                    val target = File(dir.parentFile, VaultPaths.threadDirName(threadNo, trimmed))
                    if (target != dir && !dir.renameTo(target)) throw java.io.IOException("Couldn't rename ${dir.name}")
                }
                rescan()
            }
        }

    override suspend fun mergeThreads(
        fromBoard: String, fromThreadNo: Long, intoBoard: String, intoThreadNo: Long,
    ): VaultError? = withContext(Dispatchers.IO) {
        if (!hasStorageAccess()) return@withContext VaultError.NoAccess
        if (fromBoard == intoBoard && fromThreadNo == intoThreadNo) return@withContext null
        val from = store.threadDir(fromBoard, fromThreadNo) ?: return@withContext VaultError.NotFound
        val into = store.threadDir(intoBoard, intoThreadNo) ?: return@withContext VaultError.NotFound
        attempt {
            store.lock.withLock {
                val fromMeta = store.readMeta(from) ?: VaultThreadMeta(board = fromBoard)
                // Original name -> entry under its (possibly deduped) new name. Both sidecars
                // are written once, after the moves; a failed move still records what got
                // across so no moved file is left unindexed.
                val moved = mutableMapOf<String, VaultFileMeta>()
                try {
                    for (f in fromMeta.files) {
                        val source = File(from, f.fileName)
                        if (!source.isFile) continue
                        val target = store.uniqueFile(into, f.fileName)
                        if (!store.moveFile(source, target)) throw java.io.IOException("Couldn't move ${f.fileName}")
                        val still = VideoStills.stillFor(source)
                        if (still.isFile) {
                            VideoStills.stillFor(target).let { it.parentFile?.mkdirs(); store.moveFile(still, it) }
                        }
                        moved[f.fileName] = f.copy(fileName = target.name)
                    }
                } finally {
                    if (moved.isNotEmpty()) {
                        store.updateMeta(into) { moved.values.fold(it) { acc, m -> acc.upsert(m) } }
                        store.updateMeta(from) { moved.keys.fold(it) { acc, name -> acc.remove(name) } }
                    }
                }
                store.readPosts(from)?.let { posts ->
                    store.updatePosts(into, intoBoard, intoThreadNo, posts.posts)
                }
                from.deleteRecursively()
                from.parentFile?.takeIf { it != store.root && it.listFiles()?.isEmpty() == true }?.delete()
            }
            rescan()
        }
    }

    override suspend fun rescan() = withContext(Dispatchers.IO) {
        if (!hasStorageAccess() || !store.root.isDirectory) return@withContext
        // The sidecars never held the hashes, so the old rows are the only copy. Room call,
        // kept outside the store lock.
        val previous = savedMediaDao.allOnce()
        val rebuilt = store.lock.withLock {
            store.threadMetas().flatMap { (dir, meta) ->
                meta.files.mapNotNull { f ->
                    val file = File(dir, f.fileName)
                    if (file.isFile) savedMediaEntity(meta, f, file) else null
                }
            }
        }.withHashesFrom(previous)
        savedMediaDao.replaceAll(rebuilt)
        // Stills and sound probes for videos saved before there were any. Decoding is slow,
        // so it happens after the index is usable and each row lands as its still does.
        for (row in rebuilt) {
            if (!isVideoExt(row.ext.orEmpty()) || (row.thumbnailPath != null && row.hasAudio != null)) continue
            val still = VideoStills.captureIfVideo(File(row.absolutePath)) ?: continue
            savedMediaDao.insert(
                row.copy(thumbnailPath = still.file.absolutePath, durationMs = still.durationMs, hasAudio = still.hasAudio),
            )
            recordProbe(File(row.absolutePath), still)
        }
    }

    /** Writes what a rescan learned about a video back into the sidecar, so the next rescan has it. */
    private suspend fun recordProbe(file: File, still: VideoStills.Still) {
        val dir = file.parentFile ?: return
        store.lock.withLock {
            store.updateMeta(dir) { meta ->
                val entry = meta.files.firstOrNull { it.fileName == file.name } ?: return@updateMeta meta
                meta.upsert(entry.withProbe(still))
            }
        }
    }

    override suspend fun migrateLegacyIfNeeded() = withContext(Dispatchers.IO) {
        if (preferences.data.first()[VAULT_MIGRATED] == true) return@withContext
        if (!hasStorageAccess()) return@withContext

        runCatching { migration.run() }
        preferences.edit { it[VAULT_MIGRATED] = true }
    }

    /** Coil-disk-cache-first streaming into `<target>.part`, atomically renamed on success. */
    private fun streamTo(url: String, target: File) {
        val part = File(target.parentFile, target.name + ".part")
        try {
            part.outputStream().use { out -> byteSource.copyTo(url, out) }
            if (!part.renameTo(target)) {
                part.delete()
                throw java.io.IOException("Couldn't rename ${part.name}")
            }
        } catch (e: Exception) {
            part.delete()
            throw e
        }
    }
}

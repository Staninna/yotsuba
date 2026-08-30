package dev.stan.yotsuba.data.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.stan.yotsuba.core.database.dao.SavedMediaDao
import dev.stan.yotsuba.core.media.MediaByteSource
import dev.stan.yotsuba.core.media.isVideoExt
import dev.stan.yotsuba.core.media.mimeOf
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.core.text.PostSegment
import dev.stan.yotsuba.core.text.PostText
import dev.stan.yotsuba.core.vault.VaultPostFile
import dev.stan.yotsuba.core.vault.VaultPostMeta
import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.core.database.entity.SavedMediaEntity
import dev.stan.yotsuba.core.vault.VaultPaths
import dev.stan.yotsuba.core.vault.VideoStills
import dev.stan.yotsuba.core.vault.toThreadPost
import dev.stan.yotsuba.core.vault.toVaultMeta
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val VAULT_MIGRATED = booleanPreferencesKey("vault_legacy_migrated_v1")

@Singleton
class MediaVaultRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedMediaDao: SavedMediaDao,
    private val store: VaultStore,
    private val migration: VaultLegacyMigration,
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
                val dir = File(
                    File(store.root, VaultPaths.sanitizeSegment(saveContext.board)),
                    VaultPaths.threadDirName(
                        saveContext.threadNo, saveContext.threadSubject, saveContext.opExcerpt,
                    ),
                ).apply { mkdirs() }

                val target = store.uniqueFile(
                    dir, VaultPaths.fileName(item.postNo, item.filename, item.ext),
                )
                streamTo(item.fullUrl, target)
                val still = captureStill(target)

                val savedAt = System.currentTimeMillis()
                store.lock.withLock {
                    store.updateMeta(dir) { meta ->
                        meta.copy(
                            board = saveContext.board,
                            threadNo = saveContext.threadNo,
                            subject = saveContext.threadSubject,
                            threadUrl = Urls.threadWebUrl(saveContext.board, saveContext.threadNo),
                        ).upsert(
                            store.fileMetaOf(target.name, item, saveContext.post, savedAt)
                                .copy(durationMs = still?.durationMs),
                        )
                    }
                    // Same lock as meta.json. The two writes are not one transaction, but
                    // each is atomic and a missing posts.json already means "no snapshot",
                    // so a crash between them degrades to exactly the pre-existing state.
                    store.updatePosts(
                        dir = dir,
                        board = saveContext.board,
                        threadNo = saveContext.threadNo,
                        incoming = saveContext.conversation.map { it.toVaultMeta() },
                    )
                }
                savedMediaDao.insert(
                    savedMediaEntity(
                        item, saveContext.board, saveContext.threadNo,
                        saveContext.threadSubject, target, savedAt,
                        thumbnailPath = still?.file?.absolutePath, durationMs = still?.durationMs,
                    ),
                )
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

    /** What [trash] set aside, kept in memory: a restart empties the trash anyway. */
    private class Trashed(
        val entity: SavedMediaEntity,
        val fileMeta: VaultFileMeta?,
        val dir: File,
        val trashFile: File,
    )

    private val trashed = mutableMapOf<String, Trashed>()

    override suspend fun trash(url: String): VaultError? = withContext(Dispatchers.IO) {
        val entity = savedMediaDao.byUrl(url) ?: return@withContext VaultError.NotFound
        if (entity.absolutePath.isEmpty()) return@withContext delete(url)
        attempt {
            val file = File(entity.absolutePath)
            val dir = file.parentFile ?: throw java.io.IOException("no parent for ${file.name}")
            val trashDir = File(store.root, VaultPaths.TRASH_DIR_NAME).apply { mkdirs() }
            val target = store.uniqueFile(trashDir, "${System.nanoTime()}_${file.name}")
            if (!store.moveFile(file, target)) throw java.io.IOException("Couldn't move ${file.name} to trash")
            var removed: VaultFileMeta? = null
            store.lock.withLock {
                store.updateMeta(dir) { meta ->
                    removed = meta.files.firstOrNull { it.fileName == file.name }
                    meta.remove(file.name)
                }
            }
            // The thread dir is deliberately not pruned here: an undo needs its sidecars
            // intact. Emptied directories go with the trash in [purgeTrash].
            trashed[url] = Trashed(entity, removed, dir, target)
            savedMediaDao.delete(url)
        }
    }

    override suspend fun restoreTrashed(url: String): VaultError? = withContext(Dispatchers.IO) {
        val item = trashed[url] ?: return@withContext VaultError.NotFound
        attempt {
            item.dir.mkdirs()
            val back = File(item.entity.absolutePath)
            if (!store.moveFile(item.trashFile, back)) throw java.io.IOException("Couldn't restore ${back.name}")
            store.lock.withLock {
                store.updateMeta(item.dir) { meta ->
                    item.fileMeta?.let { meta.upsert(it) } ?: meta
                }
            }
            savedMediaDao.insert(item.entity)
            trashed.remove(url)
        }
    }

    override suspend fun purgeTrash() = withContext(Dispatchers.IO) {
        if (!hasStorageAccess()) return@withContext
        val dirs = trashed.values.map { it.dir }.toSet()
        trashed.clear()
        File(store.root, VaultPaths.TRASH_DIR_NAME).deleteRecursively()
        store.lock.withLock { dirs.forEach { if (it.isDirectory) store.pruneIfEmpty(it) } }
    }

    override suspend fun exportToGallery(url: String): VaultError? = withContext(Dispatchers.IO) {
        val entity = savedMediaDao.byUrl(url) ?: return@withContext VaultError.NotFound
        val file = File(entity.absolutePath).takeIf { it.isFile } ?: return@withContext VaultError.NotFound
        attempt {
            val ext = entity.ext.orEmpty()
            val mime = mimeOf(ext)
            val video = isVideoExt(ext)
            if (Build.VERSION.SDK_INT >= 29) {
                val collection = if (video) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) +
                            "/" + VaultPaths.ROOT_DIR_NAME,
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(collection, values) ?: throw java.io.IOException("MediaStore refused ${file.name}")
                try {
                    resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                        ?: throw java.io.IOException("cannot open $uri")
                    resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(
                        if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
                    ),
                    VaultPaths.ROOT_DIR_NAME,
                ).apply { mkdirs() }
                val target = store.uniqueFile(dir, file.name)
                file.copyTo(target)
                MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
            }
        }
    }

    override suspend fun importLocalThread(
        name: String,
        sources: List<ImportSource>,
    ): VaultError? = withContext(Dispatchers.IO) {
        if (!hasStorageAccess()) return@withContext VaultError.NoAccess
        if (sources.isEmpty()) return@withContext null
        attempt {
            store.ensureRoot()
            // Epoch millis is the thread number: monotonic, unique per import, and far
            // outside the range of any real post number on the board it shares a namespace
            // with -- which is none, since _local is ours.
            val threadNo = System.currentTimeMillis()
            val dir = File(
                File(store.root, VaultPaths.LOCAL_BOARD_NAME),
                VaultPaths.threadDirName(threadNo, name),
            ).apply { mkdirs() }

            // Every file is a post of its own, numbered in pick order. No CDN URL exists, so
            // the file's own path is its key -- the same scheme rescan already uses for
            // unsorted migration leftovers.
            val files = sources.mapIndexed { index, source ->
                val postNo = (index + 1).toLong()
                val ext = VaultPaths.extensionOf(source.displayName)
                val base = source.displayName.removeSuffix(ext)
                val target = store.uniqueFile(dir, VaultPaths.fileName(postNo, base, ext))
                copyInto(source.uri, target)
                VaultFileMeta(
                    fileName = target.name,
                    postNo = postNo,
                    originalFilename = base,
                    ext = ext,
                    url = "file://" + target.absolutePath,
                    sizeBytes = target.length(),
                    savedAtMillis = threadNo,
                    durationMs = captureStill(target)?.durationMs,
                )
            }

            val meta = store.lock.withLock {
                store.updatePosts(
                    dir, VaultPaths.LOCAL_BOARD_NAME, threadNo,
                    files.map { it.toLocalPost(name, threadNo) },
                )
                store.updateMeta(dir) { meta ->
                    files.fold(
                        meta.copy(
                            board = VaultPaths.LOCAL_BOARD_NAME,
                            threadNo = threadNo,
                            subject = name,
                            threadUrl = null,
                        ),
                    ) { acc, f -> acc.upsert(f) }
                }
            }
            // The sidecar is the source of truth; the rows are derived from it exactly as
            // a rescan would derive them.
            savedMediaDao.insertAll(meta.files.map { savedMediaEntity(meta, it, File(dir, it.fileName)) })
        }
    }

    /** The synthetic post a locally imported file stands in for; the first one is the OP. */
    private fun VaultFileMeta.toLocalPost(name: String, threadNo: Long) = VaultPostMeta(
        no = postNo ?: 0L,
        isOp = postNo == 1L,
        subject = if (postNo == 1L) name else null,
        timeSeconds = threadNo / 1000,
        body = PostText(listOf(PostSegment(originalFilename.orEmpty() + ext.orEmpty()))),
        file = VaultPostFile(
            filename = originalFilename.orEmpty(),
            ext = ext.orEmpty(),
            url = url.orEmpty(),
            thumbnailUrl = "",
            sizeBytes = sizeBytes ?: 0L,
        ),
    )

    /** Copies a picked file in whole; a partial copy is deleted rather than left to confuse. */
    private fun copyInto(uri: String, target: File) {
        val stream = context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw java.io.IOException("cannot open $uri")
        try {
            stream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    override suspend fun syncSavedThreads(
        onProgress: (done: Int, total: Int) -> Unit,
    ): VaultSyncSummary = syncSavedThreads(onProgress, emptySet())

    override suspend fun syncSavedThreads(
        onProgress: (done: Int, total: Int) -> Unit,
        skip: Set<VaultLocation>,
    ): VaultSyncSummary = withContext(Dispatchers.IO) {
        if (!hasStorageAccess() || !store.root.isDirectory) return@withContext VaultSyncSummary()
        val targets = savedThreads().filterNot { VaultLocation(it.board, it.threadNo) in skip }
        runPass(targets.map { VaultLocation(it.board, it.threadNo) }, onProgress) { location, thread ->
            val dir = store.threadDir(location.board, location.threadNo) ?: return@runPass
            // The whole comment section, not just the conversation around what was
            // saved: while the thread is alive this is the only chance to take it.
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

        for ((index, target) in targets.withIndex()) {
            when (val result = threadRepository.thread(target.board, target.threadNo, forceRefresh = true)) {
                is DataResult.Success -> {
                    val outcome = attempt { store.lock.withLock { apply(target, result.value) } }
                    if (outcome == null) updated++ else failed++
                }
                is DataResult.Failure -> when (result.error) {
                    // Already gone. Whatever was captured before is all there will ever be,
                    // so this is the one moment the sidecar can be compacted.
                    NetworkError.NotFound -> {
                        gone++
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
        return VaultSyncSummary(updated = updated, gone = gone, failed = failed, pruned = pruned, rateLimited = rateLimited)
    }

    /** Under the store lock. True when the thread's sidecar was compacted this time. */
    private suspend fun pruneIfDead(board: String, threadNo: Long): Boolean {
        if (!settings.settings.first().pruneDeadSidecars) return false
        val dir = store.threadDir(board, threadNo) ?: return false
        return runCatching { store.pruneDeadThread(dir) }.getOrNull() != null
    }

    /** Saved threads that have an upstream to sync against; local imports and pruned threads do not. */
    private fun savedThreads(): List<SavedThreadDir> =
        store.threadMetas().mapNotNull { (dir, meta) ->
            if (meta.isPruned) return@mapNotNull null
            val threadNo = meta.threadNo ?: return@mapNotNull null
            val board = meta.board.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!VaultLocation(board, threadNo).isRemote) return@mapNotNull null
            SavedThreadDir(dir, board, threadNo)
        }

    private data class SavedThreadDir(val dir: File, val board: String, val threadNo: Long)

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
                val fromMeta = store.updateMeta(from) { it }
                for (f in fromMeta.files) {
                    val source = File(from, f.fileName)
                    if (!source.isFile) continue
                    val target = store.uniqueFile(into, f.fileName)
                    if (!store.moveFile(source, target)) throw java.io.IOException("Couldn't move ${f.fileName}")
                    val still = VideoStills.stillFor(source)
                    if (still.isFile) {
                        VideoStills.stillFor(target).let { it.parentFile?.mkdirs(); store.moveFile(still, it) }
                    }
                    store.updateMeta(into) { it.upsert(f.copy(fileName = target.name)) }
                    store.updateMeta(from) { it.remove(f.fileName) }
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
        val rebuilt = store.lock.withLock {
            store.threadMetas().flatMap { (dir, meta) ->
                meta.files.mapNotNull { f ->
                    val file = File(dir, f.fileName)
                    if (file.isFile) savedMediaEntity(meta, f, file) else null
                }
            }
        }
        savedMediaDao.replaceAll(rebuilt)
        // Stills for videos saved before there were any. Decoding is slow, so it happens
        // after the index is usable and each row lands as its still does.
        for (row in rebuilt) {
            if (row.thumbnailPath != null || !isVideo(row.ext)) continue
            val still = captureStill(File(row.absolutePath)) ?: continue
            savedMediaDao.insert(row.copy(thumbnailPath = still.file.absolutePath, durationMs = still.durationMs))
            recordDuration(File(row.absolutePath), still.durationMs)
        }
    }

    private fun isVideo(ext: String?) = ext?.let(::isVideoExt) == true

    /** A still and duration for a video; nothing for anything else, or when decoding fails. */
    private fun captureStill(file: File): VideoStills.Still? =
        if (isVideo(VaultPaths.extensionOf(file.name))) VideoStills.capture(file) else null

    /** Writes a duration learned during rescan back into the sidecar, so the next rescan has it. */
    private suspend fun recordDuration(file: File, durationMs: Long?) {
        durationMs ?: return
        val dir = file.parentFile ?: return
        store.lock.withLock {
            store.updateMeta(dir) { meta ->
                val entry = meta.files.firstOrNull { it.fileName == file.name } ?: return@updateMeta meta
                meta.upsert(entry.copy(durationMs = durationMs))
            }
        }
    }

    override suspend fun migrateLegacyIfNeeded() = withContext(Dispatchers.IO) {
        if (preferences.data.first()[VAULT_MIGRATED] == true) return@withContext
        if (!hasStorageAccess()) return@withContext

        runCatching { migration.run() }
        preferences.edit { it[VAULT_MIGRATED] = true }
    }

    /** Runs [block], mapping any failure to a typed [VaultError]; cancellation passes through. */
    private inline fun attempt(block: () -> Unit): VaultError? = try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        VaultError.Io(e.message)
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

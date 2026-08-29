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
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.core.text.PostSegment
import dev.stan.yotsuba.core.text.PostText
import dev.stan.yotsuba.core.vault.VaultPostFile
import dev.stan.yotsuba.core.vault.VaultPostMeta
import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.core.database.entity.SavedMediaEntity
import dev.stan.yotsuba.core.vault.VaultPaths
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
import dev.stan.yotsuba.domain.repository.ThreadRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
) : MediaVaultRepository {

    override fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    private val storageAccessState = MutableStateFlow(hasStorageAccess())
    override val storageAccess: Flow<Boolean> = storageAccessState

    override fun refreshStorageAccess() {
        storageAccessState.value = hasStorageAccess()
    }

    override fun entries(): Flow<List<VaultEntry>> = savedMediaDao.all().map { rows ->
        rows.filter { it.absolutePath.isNotEmpty() }.map { it.toVaultEntry() }
    }

    override fun saved(): Flow<Map<String, String?>> = savedMediaDao.all().map { rows ->
        rows.associate { it.url to it.absolutePath.ifEmpty { null } }
    }

    @Deprecated("Use saved().keys", ReplaceWith("saved().map { it.keys }"))
    override fun savedUrls(): Flow<Set<String>> = saved().map { it.keys }

    @Deprecated("Use saved() and drop the null paths", ReplaceWith("saved().map { it.filterValues { p -> p != null } }"))
    @Suppress("UNCHECKED_CAST")
    override fun savedPaths(): Flow<Map<String, String>> =
        saved().map { it.filterValues { path -> path != null } as Map<String, String> }

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

                val savedAt = System.currentTimeMillis()
                store.lock.withLock {
                    store.updateMeta(dir) { meta ->
                        meta.copy(
                            board = saveContext.board,
                            threadNo = saveContext.threadNo,
                            subject = saveContext.threadSubject,
                            threadUrl = Urls.threadWebUrl(saveContext.board, saveContext.threadNo),
                        ).upsert(store.fileMetaOf(target.name, item, saveContext.post, savedAt))
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
            val video = entity.ext == ".webm" || entity.ext == ".mp4"
            val mime = when (entity.ext) {
                ".jpg", ".jpeg" -> "image/jpeg"
                ".png" -> "image/png"
                ".gif" -> "image/gif"
                ".webp" -> "image/webp"
                ".webm" -> "video/webm"
                ".mp4" -> "video/mp4"
                else -> "application/octet-stream"
            }
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
    ): VaultSyncSummary = withContext(Dispatchers.IO) {
        if (!hasStorageAccess() || !store.root.isDirectory) return@withContext VaultSyncSummary()
        val targets = savedThreads()
        onProgress(0, targets.size)

        var updated = 0
        var gone = 0
        var failed = 0
        var rateLimited = false

        for ((index, target) in targets.withIndex()) {
            when (val result = threadRepository.thread(target.board, target.threadNo, forceRefresh = true)) {
                is DataResult.Success -> {
                    // The whole comment section, not just the conversation around what was
                    // saved: while the thread is alive this is the only chance to take it.
                    store.lock.withLock {
                        store.updatePosts(
                            dir = target.dir,
                            board = target.board,
                            threadNo = target.threadNo,
                            incoming = result.value.posts.map { it.toVaultMeta() },
                        )
                    }
                    updated++
                }
                is DataResult.Failure -> when (result.error) {
                    // Already gone. Whatever was captured before is all there will ever be.
                    NetworkError.NotFound -> gone++
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
        VaultSyncSummary(updated = updated, gone = gone, failed = failed, rateLimited = rateLimited)
    }

    /** Saved threads that have an upstream to sync against; local imports do not. */
    private fun savedThreads(): List<SavedThreadDir> =
        store.threadMetas().mapNotNull { (dir, meta) ->
            val threadNo = meta.threadNo ?: return@mapNotNull null
            val board = meta.board.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!VaultLocation(board, threadNo).isRemote) return@mapNotNull null
            SavedThreadDir(dir, board, threadNo)
        }

    private data class SavedThreadDir(val dir: File, val board: String, val threadNo: Long)

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
        savedMediaDao.clearAll()
        savedMediaDao.insertAll(rebuilt)
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

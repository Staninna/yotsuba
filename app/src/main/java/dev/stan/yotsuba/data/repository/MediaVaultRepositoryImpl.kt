package dev.stan.yotsuba.data.repository

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import coil3.SingletonImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.stan.yotsuba.core.database.dao.BookmarkDao
import dev.stan.yotsuba.core.database.dao.DownloadedMediaDao
import dev.stan.yotsuba.core.database.dao.HistoryDao
import dev.stan.yotsuba.core.database.dao.SavedMediaDao
import dev.stan.yotsuba.core.database.entity.SavedMediaEntity
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.core.vault.VaultMetaCodec
import dev.stan.yotsuba.core.vault.VaultPaths
import dev.stan.yotsuba.core.vault.VaultThreadMeta
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import dev.stan.yotsuba.domain.repository.VaultSaveContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val VAULT_MIGRATED = booleanPreferencesKey("vault_legacy_migrated_v1")

/** How many recent history threads the legacy migration will fetch looking for matches. */
private const val MIGRATION_HISTORY_FETCH_CAP = 25

@Singleton
class MediaVaultRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedMediaDao: SavedMediaDao,
    private val downloadedMediaDao: DownloadedMediaDao,
    private val bookmarkDao: BookmarkDao,
    private val historyDao: HistoryDao,
    private val threadRepository: ThreadRepository,
    private val preferences: DataStore<Preferences>,
) : MediaVaultRepository {

    private val root: File
        get() = File(Environment.getExternalStorageDirectory(), VaultPaths.ROOT_DIR_NAME)

    /** meta.json read-modify-write and migration must not interleave. */
    private val vaultLock = Mutex()

    override fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    override suspend fun save(item: MediaItem, saveContext: VaultSaveContext): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasStorageAccess()) return@withContext false
            runCatching {
                ensureRoot()
                val dir = File(
                    File(root, VaultPaths.sanitizeSegment(saveContext.board)),
                    VaultPaths.threadDirName(saveContext.threadNo, saveContext.threadSubject, saveContext.opExcerpt),
                ).apply { mkdirs() }

                val target = uniqueFile(dir, VaultPaths.fileName(item.postNo, item.filename, item.ext))
                if (!streamTo(item.fullUrl, target)) return@runCatching false

                val savedAt = System.currentTimeMillis()
                vaultLock.withLock {
                    updateMeta(dir) { meta ->
                        meta.copy(
                            board = saveContext.board,
                            threadNo = saveContext.threadNo,
                            subject = saveContext.threadSubject,
                            threadUrl = Urls.threadWebUrl(saveContext.board, saveContext.threadNo),
                        ).upsert(fileMetaOf(target.name, item, saveContext.post, savedAt))
                    }
                }
                savedMediaDao.insert(
                    SavedMediaEntity(
                        url = item.fullUrl,
                        board = saveContext.board,
                        threadNo = saveContext.threadNo,
                        postNo = item.postNo,
                        subject = saveContext.threadSubject,
                        displayName = target.name,
                        absolutePath = target.absolutePath,
                        ext = item.ext,
                        sizeBytes = item.sizeBytes,
                        width = item.width,
                        height = item.height,
                        thumbnailUrl = item.thumbnailUrl,
                        savedAt = savedAt,
                    ),
                )
                true
            }.getOrDefault(false)
        }

    override suspend fun delete(url: String): Boolean = withContext(Dispatchers.IO) {
        val entity = savedMediaDao.byUrl(url) ?: return@withContext false
        runCatching {
            if (entity.absolutePath.isNotEmpty()) {
                val file = File(entity.absolutePath)
                val dir = file.parentFile
                file.delete()
                if (dir != null) {
                    vaultLock.withLock {
                        updateMeta(dir) { it.remove(file.name) }
                        pruneIfEmpty(dir)
                    }
                }
            }
            savedMediaDao.delete(url)
            true
        }.getOrDefault(false)
    }

    override suspend fun rescan() = withContext(Dispatchers.IO) {
        if (!hasStorageAccess() || !root.isDirectory) return@withContext
        val rebuilt = mutableListOf<SavedMediaEntity>()
        vaultLock.withLock {
            root.walkTopDown()
                .filter { it.isFile && it.name == VaultPaths.META_FILE_NAME }
                .forEach { metaFile ->
                    val meta = VaultMetaCodec.decode(metaFile.readText()) ?: return@forEach
                    meta.files.forEach { f ->
                        val file = File(metaFile.parentFile, f.fileName)
                        if (!file.isFile) return@forEach
                        rebuilt += SavedMediaEntity(
                            // Unsorted migration leftovers have no CDN URL; key them by path.
                            url = f.url ?: "file://${file.absolutePath}",
                            board = meta.board,
                            threadNo = meta.threadNo,
                            postNo = f.postNo,
                            subject = meta.subject,
                            displayName = f.fileName,
                            absolutePath = file.absolutePath,
                            ext = f.ext,
                            sizeBytes = f.sizeBytes ?: file.length(),
                            width = f.width,
                            height = f.height,
                            thumbnailUrl = f.thumbnailUrl,
                            savedAt = f.savedAtMillis ?: file.lastModified(),
                        )
                    }
                }
        }
        savedMediaDao.clearAll()
        savedMediaDao.insertAll(rebuilt)
    }

    override suspend fun migrateLegacyIfNeeded() = withContext(Dispatchers.IO) {
        if (preferences.data.first()[VAULT_MIGRATED] == true) return@withContext
        if (!hasStorageAccess()) return@withContext

        runCatching { migrateLegacy() }
        preferences.edit { it[VAULT_MIGRATED] = true }
    }

    /**
     * Legacy layout: flat `Pictures/Yotsuba/<original filename>` plus a `downloaded_media`
     * table keyed by URL, with nothing linking file ↔ URL ↔ thread. Best effort:
     * threads still reachable via bookmarks/recent history are fetched and their posts
     * matched by original filename; everything else moves to `_unsorted/`.
     */
    private suspend fun migrateLegacy() {
        ensureRoot()
        val legacyDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            VaultPaths.ROOT_DIR_NAME,
        )
        val legacyFiles = legacyDir.listFiles { f: File -> f.isFile && f.name != VaultPaths.NOMEDIA_FILE_NAME }
            ?.toMutableList() ?: mutableListOf()
        val legacyUrls = downloadedMediaDao.allOnce().associateBy { it.url }
        if (legacyFiles.isEmpty() && legacyUrls.isEmpty()) return

        // Index posts of every thread we can still reach, keyed by attachment display name.
        data class Match(val board: String, val threadNo: Long, val subject: String?, val post: ThreadPost)
        val byDisplayName = mutableMapOf<String, Match>()
        val candidates = buildList {
            bookmarkDao.all().first().forEach { add(Triple(it.board, it.threadNo, it.subject)) }
            historyDao.all().first().take(MIGRATION_HISTORY_FETCH_CAP)
                .forEach { add(Triple(it.board, it.threadNo, it.subject)) }
        }.distinctBy { it.first to it.second }
        for ((board, threadNo, subject) in candidates) {
            val details = (threadRepository.thread(board, threadNo) as? DataResult.Success)?.value ?: continue
            details.posts.forEach { post ->
                val media = post.media ?: return@forEach
                byDisplayName.putIfAbsent(media.displayName, Match(board, threadNo, subject, post))
            }
        }

        val moved = mutableListOf<String>()
        for (file in legacyFiles) {
            // MediaStore dedupes as "name (1).jpg" — strip that before matching.
            val baseName = file.name.replace(Regex(""" \(\d+\)(\.\w+)$"""), "$1")
            val match = byDisplayName[baseName]
            val savedAt = legacyUrls[match?.post?.media?.fullUrl]?.downloadedAt ?: file.lastModified()

            if (match != null && match.post.media != null) {
                val item = match.post.media!!
                val dir = File(
                    File(root, VaultPaths.sanitizeSegment(match.board)),
                    VaultPaths.threadDirName(match.threadNo, match.subject),
                ).apply { mkdirs() }
                val target = uniqueFile(dir, VaultPaths.fileName(item.postNo, item.filename, item.ext))
                if (!moveFile(file, target)) continue
                vaultLock.withLock {
                    updateMeta(dir) { meta ->
                        meta.copy(
                            board = match.board,
                            threadNo = match.threadNo,
                            subject = match.subject,
                            threadUrl = Urls.threadWebUrl(match.board, match.threadNo),
                        ).upsert(fileMetaOf(target.name, item, match.post, savedAt))
                    }
                }
                savedMediaDao.insert(
                    SavedMediaEntity(
                        url = item.fullUrl, board = match.board, threadNo = match.threadNo,
                        postNo = item.postNo, subject = match.subject, displayName = target.name,
                        absolutePath = target.absolutePath, ext = item.ext, sizeBytes = item.sizeBytes,
                        width = item.width, height = item.height, thumbnailUrl = item.thumbnailUrl,
                        savedAt = savedAt,
                    ),
                )
            } else {
                val dir = File(root, VaultPaths.UNSORTED_DIR_NAME).apply { mkdirs() }
                val target = uniqueFile(dir, VaultPaths.sanitizeSegment(file.name))
                if (!moveFile(file, target)) continue
                vaultLock.withLock {
                    updateMeta(dir) { meta ->
                        meta.copy(board = VaultPaths.UNSORTED_DIR_NAME)
                            .upsert(
                                VaultFileMeta(
                                    fileName = target.name,
                                    originalFilename = file.nameWithoutExtension,
                                    ext = ".${file.extension}",
                                    sizeBytes = target.length(),
                                    savedAtMillis = savedAt,
                                ),
                            )
                    }
                }
                savedMediaDao.insert(
                    SavedMediaEntity(
                        url = "file://${target.absolutePath}", board = null, threadNo = null,
                        postNo = null, subject = null, displayName = target.name,
                        absolutePath = target.absolutePath, ext = ".${target.extension}",
                        sizeBytes = target.length(), width = null, height = null,
                        thumbnailUrl = null, savedAt = savedAt,
                    ),
                )
            }
            moved += file.absolutePath
        }

        // Legacy URLs with no located file still count as "already saved" for the viewer icon.
        for ((url, row) in legacyUrls) {
            if (savedMediaDao.byUrl(url) != null) continue
            val parsed = VaultPaths.parseMediaUrl(url)
            savedMediaDao.insert(
                SavedMediaEntity(
                    url = url, board = parsed?.board, threadNo = null, postNo = null, subject = null,
                    displayName = url.substringAfterLast('/'), absolutePath = "", ext = parsed?.ext,
                    sizeBytes = null, width = null, height = null, thumbnailUrl = null,
                    savedAt = row.downloadedAt,
                ),
            )
        }

        // Tell MediaStore the old files are gone so gallery apps drop their stale rows.
        if (moved.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, moved.toTypedArray(), null, null)
        }
        legacyDir.listFiles()?.takeIf { it.isEmpty() || it.all { f -> f.name == VaultPaths.NOMEDIA_FILE_NAME } }
            ?.also { legacyDir.deleteRecursively() }
    }

    private fun ensureRoot() {
        root.mkdirs()
        val nomedia = File(root, VaultPaths.NOMEDIA_FILE_NAME)
        if (!nomedia.exists()) nomedia.createNewFile()
    }

    private fun uniqueFile(dir: File, name: String): File {
        var attempt = 0
        while (true) {
            val f = File(dir, VaultPaths.dedupedFileName(name, attempt))
            if (!f.exists()) return f
            attempt++
        }
    }

    /** Coil-disk-cache-first streaming into `<target>.part`, atomically renamed on success. */
    private fun streamTo(url: String, target: File): Boolean = runCatching {
        val part = File(target.parentFile, target.name + ".part")
        part.outputStream().use { out ->
            val snapshot = SingletonImageLoader.get(context).diskCache?.openSnapshot(url)
            if (snapshot != null) {
                snapshot.use { s -> s.data.toFile().inputStream().use { it.copyTo(out) } }
            } else {
                java.net.URL(url).openStream().use { it.copyTo(out) }
            }
        }
        part.renameTo(target).also { if (!it) part.delete() }
    }.getOrDefault(false)

    private fun moveFile(from: File, to: File): Boolean = runCatching {
        if (from.renameTo(to)) return@runCatching true
        from.copyTo(to, overwrite = false)
        from.delete()
        true
    }.getOrDefault(false)

    private fun updateMeta(dir: File, transform: (VaultThreadMeta) -> VaultThreadMeta) {
        val metaFile = File(dir, VaultPaths.META_FILE_NAME)
        val current = metaFile.takeIf { it.isFile }?.let { VaultMetaCodec.decode(it.readText()) }
            ?: VaultThreadMeta(board = dir.parentFile?.name ?: "")
        val next = transform(current)
        val tmp = File(dir, VaultPaths.META_FILE_NAME + ".tmp")
        tmp.writeText(VaultMetaCodec.encode(next))
        if (!tmp.renameTo(metaFile)) {
            metaFile.writeText(VaultMetaCodec.encode(next))
            tmp.delete()
        }
    }

    /** Removes a thread dir once only meta.json (with no entries) is left; then an emptied board dir. */
    private fun pruneIfEmpty(dir: File) {
        val remaining = dir.listFiles() ?: return
        val onlyMeta = remaining.all { it.name == VaultPaths.META_FILE_NAME }
        val metaEmpty = File(dir, VaultPaths.META_FILE_NAME).takeIf { it.isFile }
            ?.let { VaultMetaCodec.decode(it.readText())?.files?.isEmpty() } ?: true
        if (onlyMeta && metaEmpty) {
            dir.deleteRecursively()
            dir.parentFile?.takeIf { it != root && it.listFiles()?.isEmpty() == true }?.delete()
        }
    }

    private fun fileMetaOf(fileName: String, item: MediaItem, post: ThreadPost?, savedAt: Long) =
        VaultFileMeta(
            fileName = fileName,
            postNo = item.postNo,
            tim = VaultPaths.parseMediaUrl(item.fullUrl)?.tim,
            originalFilename = item.filename,
            ext = item.ext,
            url = item.fullUrl,
            thumbnailUrl = item.thumbnailUrl,
            width = item.width,
            height = item.height,
            sizeBytes = item.sizeBytes,
            spoiler = item.spoiler,
            posterName = post?.name,
            tripcode = post?.tripcode,
            postedAtSeconds = post?.timeSeconds,
            postText = post?.body?.plainText?.takeIf { it.isNotBlank() },
            savedAtMillis = savedAt,
        )
}

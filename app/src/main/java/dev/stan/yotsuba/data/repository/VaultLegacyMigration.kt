package dev.stan.yotsuba.data.repository

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.stan.yotsuba.core.database.dao.BookmarkDao
import dev.stan.yotsuba.core.database.dao.DownloadedMediaDao
import dev.stan.yotsuba.core.database.dao.HistoryDao
import dev.stan.yotsuba.core.database.dao.SavedMediaDao
import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.VaultPaths
import dev.stan.yotsuba.domain.repository.ThreadRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

/** How many recent history threads the legacy migration will fetch looking for matches. */
private const val MIGRATION_HISTORY_FETCH_CAP = 25

/**
 * Legacy layout: flat `Pictures/Yotsuba/<original filename>` plus a `downloaded_media`
 * table keyed by URL, with nothing linking file ↔ URL ↔ thread. Best effort:
 * threads still reachable via bookmarks/recent history are fetched and their posts
 * matched by original filename; everything else moves to `_unsorted/`.
 */
@Singleton
class VaultLegacyMigration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: VaultStore,
    private val savedMediaDao: SavedMediaDao,
    private val downloadedMediaDao: DownloadedMediaDao,
    private val bookmarkDao: BookmarkDao,
    private val historyDao: HistoryDao,
    private val threadRepository: ThreadRepository,
) {
    private data class Match(val board: String, val threadNo: Long, val subject: String?, val post: ThreadPost)

    suspend fun run() {
        store.ensureRoot()
        val legacyDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            VaultPaths.ROOT_DIR_NAME,
        )
        val legacyFiles = legacyDir.listFiles { f: File -> f.isFile && f.name != VaultPaths.NOMEDIA_FILE_NAME }
            ?.toMutableList() ?: mutableListOf()
        val legacyUrls = downloadedMediaDao.allOnce().associateBy { it.url }
        if (legacyFiles.isEmpty() && legacyUrls.isEmpty()) return

        val byDisplayName = indexReachablePosts()

        val moved = mutableListOf<String>()
        for (file in legacyFiles) {
            // MediaStore dedupes as "name (1).jpg" — strip that before matching.
            val baseName = file.name.replace(Regex(""" \(\d+\)(\.\w+)$"""), "$1")
            val match = byDisplayName[baseName]
            val savedAt = legacyUrls[match?.post?.presentMedia?.fullUrl]?.downloadedAt ?: file.lastModified()

            val migrated = if (match?.post?.presentMedia != null) {
                migrateMatched(file, match, savedAt)
            } else {
                migrateUnsorted(file, savedAt)
            }
            if (migrated) moved += file.absolutePath
        }

        // Legacy URLs with no located file still count as "already saved" for the viewer icon.
        for ((url, row) in legacyUrls) {
            if (savedMediaDao.byUrl(url) != null) continue
            savedMediaDao.insert(urlOnlySavedMediaEntity(url, row.downloadedAt))
        }

        // Tell MediaStore the old files are gone so gallery apps drop their stale rows.
        if (moved.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, moved.toTypedArray(), null, null)
        }
        legacyDir.listFiles()?.takeIf { it.isEmpty() || it.all { f -> f.name == VaultPaths.NOMEDIA_FILE_NAME } }
            ?.also { legacyDir.deleteRecursively() }
    }

    /** Index posts of every thread we can still reach, keyed by attachment display name. */
    private suspend fun indexReachablePosts(): Map<String, Match> {
        val byDisplayName = mutableMapOf<String, Match>()
        val candidates = buildList {
            bookmarkDao.all().first().forEach { add(Triple(it.board, it.threadNo, it.subject)) }
            historyDao.all().first().take(MIGRATION_HISTORY_FETCH_CAP)
                .forEach { add(Triple(it.board, it.threadNo, it.subject)) }
        }.distinctBy { it.first to it.second }
        for ((board, threadNo, subject) in candidates) {
            val details = (threadRepository.thread(board, threadNo) as? DataResult.Success)?.value ?: continue
            details.posts.forEach { post ->
                val media = post.presentMedia ?: return@forEach
                byDisplayName.putIfAbsent(media.displayName, Match(board, threadNo, subject, post))
            }
        }
        return byDisplayName
    }

    private suspend fun migrateMatched(file: File, match: Match, savedAt: Long): Boolean {
        val item = match.post.presentMedia!!
        val dir = store.threadDirFor(match.board, match.threadNo, match.subject).apply { mkdirs() }
        val target = store.uniqueFile(dir, VaultPaths.fileName(item.postNo, item.filename, item.ext))
        if (!store.moveFile(file, target)) return false
        val row = store.lock.withLock {
            store.recordSavedFile(dir, match.board, match.threadNo, match.subject, target, item, match.post, savedAt)
        }
        savedMediaDao.insert(row)
        return true
    }

    private suspend fun migrateUnsorted(file: File, savedAt: Long): Boolean {
        val dir = File(store.root, VaultPaths.UNSORTED_DIR_NAME).apply { mkdirs() }
        val target = store.uniqueFile(dir, VaultPaths.sanitizeSegment(file.name))
        if (!store.moveFile(file, target)) return false
        store.lock.withLock {
            store.updateMeta(dir) { meta ->
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
        savedMediaDao.insert(unsortedSavedMediaEntity(target, savedAt))
        return true
    }
}

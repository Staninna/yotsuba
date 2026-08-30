package dev.stan.yotsuba.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.stan.yotsuba.core.database.dao.SavedMediaDao
import dev.stan.yotsuba.core.text.PostSegment
import dev.stan.yotsuba.core.text.PostText
import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.core.vault.VaultPaths
import dev.stan.yotsuba.core.vault.VaultPostFile
import dev.stan.yotsuba.core.vault.VaultPostMeta
import dev.stan.yotsuba.core.vault.VideoStills
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.VaultError
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.withLock

/** Assembles a thread under [VaultPaths.LOCAL_BOARD_NAME] from files the user picked. */
@Singleton
class LocalThreadImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: VaultStore,
    private val savedMediaDao: SavedMediaDao,
) {
    suspend fun import(name: String, sources: List<ImportSource>): VaultError? = attempt {
        store.ensureRoot()
        // Epoch millis is the thread number: monotonic, unique per import, and far
        // outside the range of any real post number on the board it shares a namespace
        // with -- which is none, since _local is ours.
        val threadNo = System.currentTimeMillis()
        val dir = store.threadDirFor(VaultPaths.LOCAL_BOARD_NAME, threadNo, name).apply { mkdirs() }

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
                durationMs = VideoStills.captureIfVideo(target)?.durationMs,
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
            ?: throw IOException("cannot open $uri")
        try {
            stream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }
}

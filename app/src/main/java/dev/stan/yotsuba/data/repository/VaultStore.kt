package dev.stan.yotsuba.data.repository

import android.os.Environment
import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.core.vault.VaultMetaCodec
import dev.stan.yotsuba.core.vault.VaultPaths
import dev.stan.yotsuba.core.vault.VaultPostMeta
import dev.stan.yotsuba.core.vault.VaultPostsCodec
import dev.stan.yotsuba.core.vault.VaultThreadPosts
import dev.stan.yotsuba.core.vault.VaultThreadMeta
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.ThreadPost
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

/** File-system plumbing shared by the vault repository and the legacy migration. */
@Singleton
class VaultStore @Inject constructor() {

    val root: File
        get() = File(Environment.getExternalStorageDirectory(), VaultPaths.ROOT_DIR_NAME)

    /** meta.json read-modify-write and migration must not interleave. */
    val lock = Mutex()

    fun ensureRoot() {
        root.mkdirs()
        val nomedia = File(root, VaultPaths.NOMEDIA_FILE_NAME)
        if (!nomedia.exists()) nomedia.createNewFile()
    }

    fun uniqueFile(dir: File, name: String): File {
        var attempt = 0
        while (true) {
            val f = File(dir, VaultPaths.dedupedFileName(name, attempt))
            if (!f.exists()) return f
            attempt++
        }
    }

    fun moveFile(from: File, to: File): Boolean = runCatching {
        if (from.renameTo(to)) return@runCatching true
        from.copyTo(to, overwrite = false)
        from.delete()
        true
    }.getOrDefault(false)

    fun updateMeta(dir: File, transform: (VaultThreadMeta) -> VaultThreadMeta) {
        val metaFile = File(dir, VaultPaths.META_FILE_NAME)
        val current = metaFile.takeIf { it.isFile }?.let { VaultMetaCodec.decode(it.readText()) }
            ?: VaultThreadMeta(board = dir.parentFile?.name ?: "")
        writeSidecar(metaFile, VaultMetaCodec.encode(transform(current)))
    }

    /**
     * The directory holding [threadNo] on [board]. Thread dirs are named `"<no>"` or
     * `"<no> - <slug>"`, and the slug changes with the subject, so the number is matched
     * rather than the whole name.
     */
    fun threadDir(board: String, threadNo: Long): File? {
        val boardDir = File(root, VaultPaths.sanitizeSegment(board))
        return boardDir.listFiles()
            ?.firstOrNull { it.isDirectory && it.name.substringBefore(" -").trim() == threadNo.toString() }
    }

    fun readPosts(dir: File): VaultThreadPosts? =
        File(dir, VaultPaths.POSTS_FILE_NAME).takeIf { it.isFile }
            ?.let { VaultPostsCodec.decode(it.readText()) }

    /**
     * Widens the thread's saved conversation with [incoming]. A no-op when nothing was
     * captured, so a thread whose replies were never saved gets no empty sidecar.
     */
    fun updatePosts(dir: File, board: String, threadNo: Long, incoming: List<VaultPostMeta>) {
        if (incoming.isEmpty()) return
        val current = readPosts(dir) ?: VaultThreadPosts(board = board, threadNo = threadNo)
        writeSidecar(
            File(dir, VaultPaths.POSTS_FILE_NAME),
            VaultPostsCodec.encode(current.mergedWith(incoming)),
        )
    }

    /** Write via a temp file so a crash mid-write cannot leave a half-parsed sidecar. */
    private fun writeSidecar(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    /** Removes a thread dir once only sidecars (with no entries) are left; then an emptied board dir. */
    fun pruneIfEmpty(dir: File) {
        val remaining = dir.listFiles() ?: return
        val sidecars = setOf(VaultPaths.META_FILE_NAME, VaultPaths.POSTS_FILE_NAME)
        val onlyMeta = remaining.all { it.name in sidecars }
        val metaEmpty = File(dir, VaultPaths.META_FILE_NAME).takeIf { it.isFile }
            ?.let { VaultMetaCodec.decode(it.readText())?.files?.isEmpty() } ?: true
        if (onlyMeta && metaEmpty) {
            dir.deleteRecursively()
            dir.parentFile?.takeIf { it != root && it.listFiles()?.isEmpty() == true }?.delete()
        }
    }

    fun fileMetaOf(fileName: String, item: MediaItem, post: ThreadPost?, savedAt: Long) =
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

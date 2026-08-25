package dev.stan.yotsuba.data.repository

import android.os.Environment
import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.core.vault.VaultMetaCodec
import dev.stan.yotsuba.core.vault.VaultPaths
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
        val next = transform(current)
        val tmp = File(dir, VaultPaths.META_FILE_NAME + ".tmp")
        tmp.writeText(VaultMetaCodec.encode(next))
        if (!tmp.renameTo(metaFile)) {
            metaFile.writeText(VaultMetaCodec.encode(next))
            tmp.delete()
        }
    }

    /** Removes a thread dir once only meta.json (with no entries) is left; then an emptied board dir. */
    fun pruneIfEmpty(dir: File) {
        val remaining = dir.listFiles() ?: return
        val onlyMeta = remaining.all { it.name == VaultPaths.META_FILE_NAME }
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

package dev.stan.yotsuba.data.repository

import android.os.Environment
import dev.stan.yotsuba.core.database.entity.SavedMediaEntity
import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.core.vault.VaultMetaCodec
import dev.stan.yotsuba.core.vault.VaultPaths
import dev.stan.yotsuba.core.vault.VaultPostMeta
import dev.stan.yotsuba.core.vault.VaultPostsCodec
import dev.stan.yotsuba.core.vault.VaultThreadPosts
import dev.stan.yotsuba.core.vault.VaultThreadMeta
import dev.stan.yotsuba.core.vault.VideoStills
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.core.vault.toThreadPost
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.PostGraph
import dev.stan.yotsuba.domain.model.ThreadPost
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex

/** File-system plumbing shared by the vault repository and the legacy migration. */
@Singleton
class VaultStore(private val rootOverride: File?) {

    @Inject constructor() : this(null)

    val root: File
        get() = rootOverride ?: File(Environment.getExternalStorageDirectory(), VaultPaths.ROOT_DIR_NAME)

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

    /** The thread's decoded meta.json, or null when there is none (or it will not parse). */
    fun readMeta(dir: File): VaultThreadMeta? =
        File(dir, VaultPaths.META_FILE_NAME).takeIf { it.isFile }
            ?.let { VaultMetaCodec.decode(it.readText()) }

    /** Read-modify-writes the thread's meta.json; returns what was written. */
    fun updateMeta(dir: File, transform: (VaultThreadMeta) -> VaultThreadMeta): VaultThreadMeta {
        val current = readMeta(dir) ?: VaultThreadMeta(board = dir.parentFile?.name ?: "")
        val next = transform(current)
        writeAtomically(File(dir, VaultPaths.META_FILE_NAME), VaultMetaCodec.encode(next))
        return next
    }

    /**
     * Files [target] under its thread in meta.json and returns the index row for it. The
     * row is the caller's to insert; Room stays outside [lock], which this must be held under.
     */
    fun recordSavedFile(
        dir: File,
        board: String,
        threadNo: Long,
        subject: String?,
        target: File,
        item: MediaItem,
        post: ThreadPost?,
        savedAt: Long,
        still: VideoStills.Still? = null,
    ): SavedMediaEntity {
        updateMeta(dir) { meta ->
            meta.copy(
                board = board,
                threadNo = threadNo,
                subject = subject,
                threadUrl = Urls.threadWebUrl(board, threadNo),
            ).upsert(fileMetaOf(target.name, item, post, savedAt).copy(durationMs = still?.durationMs))
        }
        return savedMediaEntity(
            item, board, threadNo, subject, target, savedAt,
            thumbnailPath = still?.file?.absolutePath, durationMs = still?.durationMs,
        )
    }

    private fun boardDir(board: String): File = File(root, VaultPaths.sanitizeSegment(board))

    /**
     * Where a thread's directory goes when it is first written: the name is built from the
     * subject, so [threadDir] is the lookup for one that may already exist under another slug.
     */
    fun threadDirFor(board: String, threadNo: Long, subject: String?, opExcerpt: String? = null): File =
        File(boardDir(board), VaultPaths.threadDirName(threadNo, subject, opExcerpt))

    /**
     * The directory holding [threadNo] on [board]. Thread dirs are named `"<no>"` or
     * `"<no> - <slug>"`, and the slug changes with the subject, so the number is matched
     * rather than the whole name.
     */
    fun threadDir(board: String, threadNo: Long): File? =
        boardDir(board).listFiles()
            ?.firstOrNull { it.isDirectory && it.name.substringBefore(" -").trim() == threadNo.toString() }

    /** A thread directory and its decoded meta.json. */
    data class StoredThread(val dir: File, val meta: VaultThreadMeta)

    /**
     * Every thread directory under the root that carries a readable meta.json -- the whole
     * index, as far as the sidecars know it. Empty when the root is missing.
     */
    fun threadMetas(): List<StoredThread> =
        root.takeIf { it.isDirectory }?.walkTopDown().orEmpty()
            .filter { it.isFile && it.name == VaultPaths.META_FILE_NAME }
            .mapNotNull { metaFile ->
                val dir = metaFile.parentFile ?: return@mapNotNull null
                val meta = VaultMetaCodec.decode(metaFile.readText()) ?: return@mapNotNull null
                StoredThread(dir, meta)
            }
            .toList()

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
        writeAtomically(
            File(dir, VaultPaths.POSTS_FILE_NAME),
            VaultPostsCodec.encode(current.mergedWith(incoming)),
        )
    }

    /**
     * Writes the whole live thread into the sidecars, creating the directory when this is
     * the first thing captured for it. Same merge as a save: posts already recorded are
     * replaced, never duplicated. Refuses a pruned thread, which is final.
     */
    fun snapshot(board: String, threadNo: Long, subject: String?, opExcerpt: String?, posts: List<VaultPostMeta>): File? {
        val dir = threadDir(board, threadNo) ?: threadDirFor(board, threadNo, subject, opExcerpt)
        if (readMeta(dir)?.isPruned == true) return null
        dir.mkdirs()
        updatePosts(dir, board, threadNo, posts)
        updateMeta(dir) {
            it.copy(
                board = board,
                threadNo = threadNo,
                subject = subject ?: it.subject,
                threadUrl = Urls.threadWebUrl(board, threadNo),
                snapshotAt = System.currentTimeMillis(),
            )
        }
        return dir
    }

    /**
     * Compacts a dead thread's posts.json to the OP plus the conversation around every post
     * that has a saved file on disk, and marks meta.json so it never happens twice. Returns
     * how many posts were dropped, or null when there was nothing to do: already pruned, no
     * posts captured, or no media saved -- a snapshot-only thread is the only copy and is
     * left whole.
     */
    fun pruneDeadThread(dir: File): Int? {
        val meta = readMeta(dir) ?: return null
        if (meta.isPruned) return null
        val savedPostNos = meta.files
            .filter { File(dir, it.fileName).isFile }
            .mapNotNull { it.postNo }
            .toSet()
        if (savedPostNos.isEmpty()) return null
        val saved = readPosts(dir) ?: return null
        val threadPosts = saved.posts.map { it.toThreadPost(meta.board) }
        val graph = PostGraph(threadPosts, PostGraph.backlinksOf(threadPosts))
        val keep = buildSet {
            saved.posts.filter { it.isOp }.forEach { add(it.no) }
            savedPostNos.forEach { no -> graph.conversationAround(no).forEach { add(it.no) } }
        }
        val kept = saved.posts.filter { it.no in keep }
        val dropped = saved.posts.size - kept.size
        writeAtomically(File(dir, VaultPaths.POSTS_FILE_NAME), VaultPostsCodec.encode(saved.copy(posts = kept)))
        updateMeta(dir) { it.copy(prunedAt = System.currentTimeMillis(), prunedPostCount = dropped) }
        return dropped
    }

    /** Write via a temp file so a crash mid-write cannot leave a half-parsed file. */
    fun writeAtomically(file: File, text: String) {
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
        val sidecars = setOf(VaultPaths.META_FILE_NAME, VaultPaths.POSTS_FILE_NAME, VaultPaths.THUMBS_DIR_NAME)
        val onlyMeta = remaining.all { it.name in sidecars }
        // A snapshot without files is not an emptied directory; the sidecar is the point.
        val metaEmpty = readMeta(dir)?.let { it.files.isEmpty() && it.snapshotAt == null } ?: true
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

/** Runs [block], mapping any failure to a typed [VaultError]; cancellation passes through. */
internal inline fun attempt(block: () -> Unit): VaultError? = try {
    block()
    null
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    VaultError.Io(e.message)
}

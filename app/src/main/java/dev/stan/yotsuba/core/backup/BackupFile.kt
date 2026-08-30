package dev.stan.yotsuba.core.backup

import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.model.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * On-disk shape of `yotsuba-backup.json`. Every field past [version] has a default so a file
 * from a newer build still opens on an older one; unknown keys are dropped, not fatal.
 */
@Serializable
data class BackupFile(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long = 0L,
    val settings: Settings = Settings(),
    val bookmarks: List<BackupBookmark> = emptyList(),
    val hiddenThreads: List<BackupHiddenThread> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val FILE_NAME = "yotsuba-backup.json"
    }
}

@Serializable
data class BackupBookmark(
    val board: String,
    val threadNo: Long,
    val subject: String? = null,
    val opExcerpt: String = "",
    val thumbnailUrl: String? = null,
    val replyCount: Int = 0,
    val imageCount: Int = 0,
    val bookmarkedAt: Long = 0L,
    val state: String = BookmarkState.UNKNOWN.name,
    val readUpTo: Long? = null,
    val pinned: Boolean = false,
    val lastActivityAt: Long? = null,
)

@Serializable
data class BackupHiddenThread(val board: String, val threadNo: Long)

/** Encoding, decoding and the merge rules, kept free of Android so the JVM tests cover them. */
object BackupCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(raw: String): BackupFile = json.decodeFromString(BackupFile.serializer(), raw)

    fun build(
        exportedAt: Long,
        settings: Settings,
        bookmarks: List<Bookmark>,
        hidden: List<HiddenThread>,
    ) = BackupFile(
        exportedAt = exportedAt,
        settings = settings,
        bookmarks = bookmarks.map { it.toBackup() },
        hiddenThreads = hidden.map { BackupHiddenThread(it.board, it.threadNo) },
    )

    /**
     * The rows [import][dev.stan.yotsuba.domain.repository.BackupRepository.import] should
     * upsert: one per backup bookmark, merged onto the existing row when there is one. The
     * read mark is the higher of the two; pinned sticks if either side has it. Existing rows
     * absent from the backup are left alone.
     */
    fun mergeBookmarks(existing: List<Bookmark>, incoming: List<BackupBookmark>): List<Bookmark> {
        val current = existing.associateBy { it.board to it.threadNo }
        return incoming.map { row ->
            val old = current[row.board to row.threadNo]
            if (old == null) row.toDomain() else old.copy(
                readUpTo = maxOfNullable(old.readUpTo, row.readUpTo),
                pinned = old.pinned || row.pinned,
            )
        }
    }

    /** Hidden threads from the backup that are not already hidden. */
    fun newHiddenThreads(existing: List<HiddenThread>, incoming: List<BackupHiddenThread>): List<HiddenThread> {
        val current = existing.map { it.board to it.threadNo }.toSet()
        return incoming
            .filter { (it.board to it.threadNo) !in current }
            .distinctBy { it.board to it.threadNo }
            .map { HiddenThread(it.board, it.threadNo) }
    }

    private fun maxOfNullable(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }

    private fun Bookmark.toBackup() = BackupBookmark(
        board = board,
        threadNo = threadNo,
        subject = subject,
        opExcerpt = opExcerpt,
        thumbnailUrl = thumbnailUrl,
        replyCount = replyCount,
        imageCount = imageCount,
        bookmarkedAt = bookmarkedAt,
        state = state.name,
        readUpTo = readUpTo,
        pinned = pinned,
        lastActivityAt = lastActivityAt,
    )

    private fun BackupBookmark.toDomain() = Bookmark(
        board = board,
        threadNo = threadNo,
        subject = subject,
        opExcerpt = opExcerpt,
        thumbnailUrl = thumbnailUrl,
        replyCount = replyCount,
        imageCount = imageCount,
        bookmarkedAt = bookmarkedAt,
        lastCheckedAt = null,
        // A state name from a newer or older backup is a legitimate UNKNOWN, not a failure.
        state = runCatching { BookmarkState.valueOf(state) }.getOrDefault(BookmarkState.UNKNOWN),
        readUpTo = readUpTo,
        pinned = pinned,
        lastActivityAt = lastActivityAt,
    )
}

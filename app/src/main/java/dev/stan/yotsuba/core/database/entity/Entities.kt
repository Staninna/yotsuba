package dev.stan.yotsuba.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks", primaryKeys = ["board", "threadNo"])
data class BookmarkEntity(
    val board: String,
    val threadNo: Long,
    val subject: String?,
    val opExcerpt: String,
    val thumbnailUrl: String?,
    val replyCount: Int,
    val imageCount: Int,
    val bookmarkedAt: Long,
    val lastCheckedAt: Long?,
    val lastSeenPostNo: Long?,
    val state: String, // ALIVE | ARCHIVED | DEAD | UNKNOWN (D9)
    /** Superseded by readUpTo/postNos; no longer written, dropped in a later migration. */
    val newReplies: Int = 0,
    /** Superseded by readUpTo/postNos; no longer written, dropped in a later migration. */
    val unreadCount: Int = 0,
    /** The one read mark: the newest post the user has had on screen. Only markSeen writes it. */
    val readUpTo: Long? = null,
    /** Comma-separated post numbers as of the last refresh; unread = those past readUpTo. */
    val postNos: String = "",
    val pinned: Boolean = false,
    /** Catalog last_modified (epoch ms) as of the last refresh. */
    val lastActivityAt: Long? = null,
)

@Entity(tableName = "history", primaryKeys = ["board", "threadNo"])
data class HistoryEntity(
    val board: String,
    val threadNo: Long,
    val subject: String?,
    val opExcerpt: String,
    val thumbnailUrl: String?,
    val viewedAt: Long,
    val lastScrollPostNo: Long?,
    /** Highest post number that has actually been on screen — the true "read up to" mark. */
    val maxReadPostNo: Long? = null,
)

@Entity(tableName = "hidden_threads", primaryKeys = ["board", "threadNo"])
data class HiddenThreadEntity(
    val board: String,
    val threadNo: Long,
    val hiddenAt: Long,
)

/** Media files the user saved to the gallery, keyed by full URL. Legacy — superseded by [SavedMediaEntity]. */
@Entity(tableName = "downloaded_media")
data class DownloadedMediaEntity(
    @PrimaryKey val url: String,
    val downloadedAt: Long,
)

/**
 * Media files saved into the on-disk vault (`/sdcard/Yotsuba/…`), keyed by full CDN URL.
 * Mirrors the per-thread `meta.json` sidecars; rebuildable from disk via rescan.
 */
@Entity(tableName = "saved_media")
data class SavedMediaEntity(
    @PrimaryKey val url: String,
    val board: String?,
    val threadNo: Long?,
    val postNo: Long?,
    val subject: String?,
    /** File name on disk (inside its thread directory). */
    val displayName: String,
    /** Absolute path of the saved file; empty when the file was never located (legacy rows). */
    val absolutePath: String,
    val ext: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    /** Remote thumbnail, used for video previews in the explorer grid. */
    val thumbnailUrl: String?,
    val savedAt: Long,
)

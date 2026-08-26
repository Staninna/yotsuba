package dev.stan.yotsuba.core.backup

import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.Density
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.HistoryRetention
import dev.stan.yotsuba.domain.model.MediaAutoplay
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThemeMode
import dev.stan.yotsuba.domain.model.ThumbnailSize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The on-disk backup format.
 *
 * Deliberately not the Room entities: those change shape whenever the schema
 * does, and a backup has to be readable by a build that isn't the one that
 * wrote it. Enums travel as their names and unknown values fall back to the
 * default rather than failing the whole import.
 *
 * Saved media is absent on purpose — it already lives on shared storage with
 * its own meta.json sidecars, and survives an uninstall untouched.
 */
@Serializable
data class BackupFile(
    val version: Int = CURRENT_VERSION,
    val exportedAtMs: Long = 0L,
    val appVersion: String = "",
    val settings: SettingsBackup = SettingsBackup(),
    val bookmarks: List<BookmarkBackup> = emptyList(),
    val history: List<HistoryBackup> = emptyList(),
    val hiddenThreads: List<HiddenThreadBackup> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val FILE_NAME = "yotsuba-backup.json"

        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }
    }
}

@Serializable
data class SettingsBackup(
    val themeMode: String = ThemeMode.SYSTEM.name,
    val dynamicColor: Boolean = true,
    val catalogLayout: String = CatalogLayout.COMFORTABLE.name,
    val thumbnailSize: String = ThumbnailSize.MEDIUM.name,
    val density: String = Density.COMFORTABLE.name,
    val revealAllSpoilers: Boolean = false,
    val autoRefreshEnabled: Boolean = false,
    val confirmBeforeOpeningLinks: Boolean = true,
    val trustedDomains: List<String> = emptyList(),
    val mediaAutoplay: String = MediaAutoplay.UNMETERED_ONLY.name,
    val recordHistory: Boolean = true,
    val historyRetention: String = HistoryRetention.FOREVER.name,
    val favouriteBoards: List<String> = emptyList(),
    val hiddenBoards: List<String> = emptyList(),
    val hiddenCategories: List<String> = emptyList(),
)

@Serializable
data class BookmarkBackup(
    val board: String,
    val threadNo: Long,
    val subject: String? = null,
    val opExcerpt: String = "",
    val thumbnailUrl: String? = null,
    val replyCount: Int = 0,
    val imageCount: Int = 0,
    val bookmarkedAt: Long = 0L,
    val lastCheckedAt: Long? = null,
    val lastSeenPostNo: Long? = null,
    val state: String = BookmarkState.UNKNOWN.name,
)

@Serializable
data class HistoryBackup(
    val board: String,
    val threadNo: Long,
    val subject: String? = null,
    val opExcerpt: String = "",
    val thumbnailUrl: String? = null,
    val viewedAt: Long = 0L,
    val lastScrollPostNo: Long? = null,
)

@Serializable
data class HiddenThreadBackup(val board: String, val threadNo: Long)

/** An unrecognised name means a newer build wrote it; take the default, don't throw. */
private inline fun <reified T : Enum<T>> enumOr(name: String, fallback: T): T =
    runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)

fun Settings.toBackup() = SettingsBackup(
    themeMode = themeMode.name,
    dynamicColor = dynamicColor,
    catalogLayout = catalogLayout.name,
    thumbnailSize = thumbnailSize.name,
    density = density.name,
    revealAllSpoilers = revealAllSpoilers,
    autoRefreshEnabled = autoRefreshEnabled,
    confirmBeforeOpeningLinks = confirmBeforeOpeningLinks,
    trustedDomains = trustedDomains.sorted(),
    mediaAutoplay = mediaAutoplay.name,
    recordHistory = recordHistory,
    historyRetention = historyRetention.name,
    favouriteBoards = favouriteBoards.sorted(),
    hiddenBoards = hiddenBoards.sorted(),
    hiddenCategories = hiddenCategories.sorted(),
)

/**
 * Applied onto the *current* settings, so a field this backup predates keeps
 * whatever the running app has rather than snapping back to a default. The
 * update token is never carried: it is a credential, not a preference.
 */
fun SettingsBackup.applyTo(current: Settings) = current.copy(
    themeMode = enumOr(themeMode, current.themeMode),
    dynamicColor = dynamicColor,
    catalogLayout = enumOr(catalogLayout, current.catalogLayout),
    thumbnailSize = enumOr(thumbnailSize, current.thumbnailSize),
    density = enumOr(density, current.density),
    revealAllSpoilers = revealAllSpoilers,
    autoRefreshEnabled = autoRefreshEnabled,
    confirmBeforeOpeningLinks = confirmBeforeOpeningLinks,
    trustedDomains = trustedDomains.toSet(),
    mediaAutoplay = enumOr(mediaAutoplay, current.mediaAutoplay),
    recordHistory = recordHistory,
    historyRetention = enumOr(historyRetention, current.historyRetention),
    favouriteBoards = favouriteBoards.toSet(),
    hiddenBoards = hiddenBoards.toSet(),
    hiddenCategories = hiddenCategories.toSet(),
)

fun Bookmark.toBackup() = BookmarkBackup(
    board = board,
    threadNo = threadNo,
    subject = subject,
    opExcerpt = opExcerpt,
    thumbnailUrl = thumbnailUrl,
    replyCount = replyCount,
    imageCount = imageCount,
    bookmarkedAt = bookmarkedAt,
    lastCheckedAt = lastCheckedAt,
    lastSeenPostNo = lastSeenPostNo,
    state = state.name,
)

fun BookmarkBackup.toDomain() = Bookmark(
    board = board,
    threadNo = threadNo,
    subject = subject,
    opExcerpt = opExcerpt,
    thumbnailUrl = thumbnailUrl,
    replyCount = replyCount,
    imageCount = imageCount,
    bookmarkedAt = bookmarkedAt,
    lastCheckedAt = lastCheckedAt,
    lastSeenPostNo = lastSeenPostNo,
    state = enumOr(state, BookmarkState.UNKNOWN),
)

fun HistoryEntry.toBackup() = HistoryBackup(
    board = board,
    threadNo = threadNo,
    subject = subject,
    opExcerpt = opExcerpt,
    thumbnailUrl = thumbnailUrl,
    viewedAt = viewedAt,
    lastScrollPostNo = lastScrollPostNo,
)

fun HistoryBackup.toDomain() = HistoryEntry(
    board = board,
    threadNo = threadNo,
    subject = subject,
    opExcerpt = opExcerpt,
    thumbnailUrl = thumbnailUrl,
    viewedAt = viewedAt,
    lastScrollPostNo = lastScrollPostNo,
)

fun HiddenThread.toBackup() = HiddenThreadBackup(board, threadNo)

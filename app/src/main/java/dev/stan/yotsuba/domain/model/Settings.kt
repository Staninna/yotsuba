package dev.stan.yotsuba.domain.model

import kotlinx.serialization.Serializable

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class CatalogLayout { COMFORTABLE, COMPACT, LIST }
enum class MediaAutoplay { ALWAYS, UNMETERED_ONLY, NEVER }
enum class HistoryRetention { FOREVER, DAYS_30, DAYS_7 }

/** What a tap on a `>>123` quotelink does; a long-press does the other one. */
enum class QuoteTapAction { POPOVER, JUMP }

/**
 * How a file that only exists on this phone reaches a reverse search engine. Direct upload
 * posts it to the engine's own form; the temporary host puts it on litterbox for an hour
 * and hands the engine that URL. Engines whose form gives no shareable result page use the
 * host either way.
 */
enum class LocalSearchMethod { DIRECT_UPLOAD, TEMP_HOST }

/** How far one double-tap on a video's left or right edge jumps. */
enum class SeekStep(val seconds: Int) {
    FIVE(5),
    TEN(10),
    FIFTEEN(15),
    THIRTY(30),
}

/** Post text size, as a multiplier on the theme's base sizes. */
enum class FontSize(val scale: Float) {
    SMALL(0.875f),
    DEFAULT(1f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f),
}

/** Post text line height, in em so it tracks whichever [FontSize] is set. */
enum class LineSpacing(val em: Float) {
    COMPACT(1.25f),
    DEFAULT(1.45f),
    RELAXED(1.7f),
}

/**
 * Persisted as one JSON blob. Every field needs a default: the serializer coerces missing
 * keys and unknown enum names to it, which is how old installs survive new fields.
 */
@Serializable
data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val catalogLayout: CatalogLayout = CatalogLayout.COMFORTABLE,
    val revealAllSpoilers: Boolean = false,
    val autoRefreshEnabled: Boolean = false,
    /**
     * In a watched thread with unread posts, fold everything between the OP and the read
     * mark into one "N earlier posts" row, so the thread opens at what is new.
     */
    val collapseReadPosts: Boolean = true,
    val confirmBeforeOpeningLinks: Boolean = true,
    val trustedDomains: Set<String> = emptySet(),
    val mediaAutoplay: MediaAutoplay = MediaAutoplay.UNMETERED_ONLY,
    /** Hold the display awake while a video is playing in the viewer. */
    val keepScreenOnWhileWatching: Boolean = true,
    val doubleTapSeekEnabled: Boolean = true,
    val seekStep: SeekStep = SeekStep.TEN,
    /** Long-press a thumbnail or an open image to send it to the vault. */
    val holdToSave: Boolean = true,
    /**
     * Save the text of every transitive parent and reply of a post alongside its media,
     * so the context survives the thread being pruned. Text only, never bytes.
     */
    val saveRepliesWithMedia: Boolean = true,
    /**
     * Background pass writes every live bookmarked thread's comment section into the vault
     * as a sidecar-only directory, so a watched thread is readable after it 404s even when
     * nothing was saved from it.
     */
    val snapshotWatchedThreads: Boolean = true,
    /**
     * Once a thread is gone, compact its posts.json to the OP plus the conversation around
     * each saved file. Off by default: the whole dead thread is kept. A thread with no
     * saved files is never pruned either way.
     */
    val pruneDeadSidecars: Boolean = false,
    val recordHistory: Boolean = true,
    val historyRetention: HistoryRetention = HistoryRetention.FOREVER,
    /** Ask before removing a saved thread from the vault. */
    val confirmVaultDelete: Boolean = true,
    /** How often bookmarked threads are polled for new replies in the background. */
    val bookmarkRefreshMinutes: Int = 30,
    /** Post a notification when a bookmarked thread gains replies. */
    val bookmarkNotifications: Boolean = true,
    /** Prefer thumbnails and skip prefetching full media until it is opened. */
    val dataSaver: Boolean = false,
    /** Turn off transitions and list animations regardless of the system animator scale. */
    val reduceMotion: Boolean = false,
    val favouriteBoards: Set<String> = emptySet(),
    /** The only board-visibility state: a board is shown unless its code is in here. */
    val hiddenBoards: Set<String> = emptySet(),
    /**
     * Legacy. Older installs hid whole categories by name. [BoardsViewModel] folds every
     * board in these into [hiddenBoards] the first time it sees the board list and clears
     * this; nothing writes to it any more.
     */
    val hiddenCategories: Set<String> = emptySet(),
    /** Content filters, applied in order; the first match wins. */
    val filters: List<Filter> = emptyList(),
    /** Tap on a quotelink opens the preview stack or jumps to the post in place. */
    val quoteTap: QuoteTapAction = QuoteTapAction.POPOVER,
    /** Post text only; chrome and the settings screens follow the system size. */
    val fontSize: FontSize = FontSize.DEFAULT,
    val lineSpacing: LineSpacing = LineSpacing.DEFAULT,
    /**
     * Tapping a thumbnail in a thread shows the full image in place of it. Still images only;
     * videos, gifs and sound posts keep opening the viewer.
     */
    val inlineImageExpansion: Boolean = false,
    /** Ask for the phone's own unlock (biometrics or PIN) before showing the app. */
    val appLock: Boolean = false,
    /**
     * How long the app may sit in the background before it locks again; 0 locks the moment
     * it leaves the screen. Only read while [appLock] is on.
     */
    val appLockDelaySeconds: Int = 0,
    /** Sort order of the Watched list in the Threads tab. */
    val bookmarkSortOrder: BookmarkSortOrder = BookmarkSortOrder.UNREAD_FIRST,
    /** How local-only files reach the reverse search engines that need a URL. */
    val localSearchMethod: LocalSearchMethod = LocalSearchMethod.DIRECT_UPLOAD,
    /** Ask before a local-only file goes to the temporary host. */
    val confirmTemporaryHost: Boolean = true,
)

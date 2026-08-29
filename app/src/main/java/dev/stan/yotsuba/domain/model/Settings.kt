package dev.stan.yotsuba.domain.model

import kotlinx.serialization.Serializable

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class CatalogLayout { COMFORTABLE, COMPACT, LIST }
enum class MediaAutoplay { ALWAYS, UNMETERED_ONLY, NEVER }
enum class HistoryRetention { FOREVER, DAYS_30, DAYS_7 }

/** How far one double-tap on a video's left or right edge jumps. */
enum class SeekStep(val seconds: Int) {
    FIVE(5),
    TEN(10),
    FIFTEEN(15),
    THIRTY(30),
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
    val hiddenBoards: Set<String> = emptySet(),
    val hiddenCategories: Set<String> = emptySet(),
    /** Content filters, applied in order; the first match wins. */
    val filters: List<Filter> = emptyList(),
)

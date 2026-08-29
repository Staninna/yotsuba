package dev.stan.yotsuba.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class CatalogLayout { COMFORTABLE, COMPACT, LIST }
enum class MediaAutoplay { ALWAYS, UNMETERED_ONLY, NEVER }
enum class HistoryRetention { FOREVER, DAYS_30, DAYS_7 }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val catalogLayout: CatalogLayout = CatalogLayout.COMFORTABLE,
    val revealAllSpoilers: Boolean = false,
    val autoRefreshEnabled: Boolean = false,
    val confirmBeforeOpeningLinks: Boolean = true,
    val trustedDomains: Set<String> = emptySet(),
    val mediaAutoplay: MediaAutoplay = MediaAutoplay.UNMETERED_ONLY,
    val recordHistory: Boolean = true,
    val historyRetention: HistoryRetention = HistoryRetention.FOREVER,
    val favouriteBoards: Set<String> = emptySet(),
    val hiddenBoards: Set<String> = emptySet(),
    val hiddenCategories: Set<String> = emptySet(),
)

package dev.stan.yotsuba.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Boards : Route
    @Serializable data class Catalog(val board: String, val searchQuery: String? = null) : Route
    @Serializable data class Thread(
        val board: String,
        val threadNo: Long,
        val scrollToPostNo: Long? = null,
    ) : Route
    @Serializable data class Media(val board: String, val threadNo: Long, val initialPostNo: Long) : Route
    @Serializable data object Bookmarks : Route
    @Serializable data object Vault : Route
    @Serializable data object History : Route
    @Serializable data object Settings : Route
    @Serializable data class SettingsSection(val id: SettingsSectionId) : Route
}

/** The subscreens reachable from the settings index. */
enum class SettingsSectionId {
    APPEARANCE,
    READING,
    MEDIA,
    BOARDS,
    LINKS,
    STORAGE,
    UPDATES,
    ABOUT,
}

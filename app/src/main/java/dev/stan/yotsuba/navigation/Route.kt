package dev.stan.yotsuba.navigation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data object Boards : Route
    @Serializable data class Catalog(val board: String, val searchQuery: String? = null) : Route
    @Serializable data class Thread(
        val board: String,
        val threadNo: Long,
        val scrollToPostNo: Long? = null,
    ) : Route
    @Serializable data class Media(val board: String, val threadNo: Long, val initialPostNo: Long) : Route
    @Serializable data object Threads : Route
    @Serializable data object Vault : Route
    @Serializable data object Settings : Route
    @Serializable data class SettingsSection(val id: SettingsSectionId) : Route
}

/**
 * The subscreens reachable from the settings index.
 *
 * [Keep] is load-bearing, not decoration: navigation resolves a route's argument types by
 * fully qualified name at graph-construction time, so R8 renaming this enum crashes the
 * app on launch in a minified build -- and only in a minified build, which is why debug
 * never shows it.
 */
@Keep
enum class SettingsSectionId { APPEARANCE, READING, MEDIA, BOARDS, LINKS, PRIVACY, FILTERS, STORAGE, UPDATES, ABOUT }

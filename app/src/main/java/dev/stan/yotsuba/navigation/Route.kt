package dev.stan.yotsuba.navigation

import androidx.annotation.Keep
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.ui.graphics.vector.ImageVector
import dev.stan.yotsuba.R
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
enum class SettingsSectionId(val titleRes: Int, val icon: ImageVector) {
    APPEARANCE(R.string.settings_appearance, Icons.Outlined.Palette),
    READING(R.string.settings_reading, Icons.Outlined.MenuBook),
    MEDIA(R.string.settings_media, Icons.Outlined.PlayCircleOutline),
    BOARDS(R.string.settings_boards, Icons.Outlined.GridView),
    LINKS(R.string.settings_links, Icons.Outlined.Shield),
    FILTERS(R.string.settings_filters, Icons.Outlined.FilterAlt),
    STORAGE(R.string.settings_storage, Icons.Outlined.Storage),
    UPDATES(R.string.settings_updates, Icons.Outlined.SystemUpdateAlt),
    ABOUT(R.string.settings_about, Icons.Outlined.Info),
}

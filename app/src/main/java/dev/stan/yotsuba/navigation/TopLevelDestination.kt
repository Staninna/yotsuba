package dev.stan.yotsuba.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import dev.stan.yotsuba.R

enum class TopLevelDestination(
    val route: Route,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    BOARDS(Route.Boards, R.string.tab_boards, Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List),
    BOOKMARKS(Route.Bookmarks, R.string.tab_bookmarks, Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
    HISTORY(Route.History, R.string.tab_history, Icons.Filled.History, Icons.Outlined.History),
    VAULT(Route.Vault, R.string.tab_vault, Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    SETTINGS(Route.Settings, R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}

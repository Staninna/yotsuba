package dev.stan.yotsuba.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import dev.stan.yotsuba.R

/** The tab row, in order. Settings is not a tab: every tab reaches it from a gear icon. */
enum class TopLevelDestination(
    val route: Route,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(Route.Home, R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    BOARDS(Route.Boards, R.string.tab_boards, Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List),
    THREADS(Route.Threads, R.string.tab_threads, Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks),
    VAULT(Route.Vault, R.string.tab_vault, Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
}

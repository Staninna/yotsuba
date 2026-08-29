package dev.stan.yotsuba.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.window.core.layout.WindowWidthSizeClass
import dev.stan.yotsuba.feature.bookmarks.BookmarksScreen
import dev.stan.yotsuba.feature.boards.BoardsScreen
import dev.stan.yotsuba.feature.catalog.CatalogScreen
import dev.stan.yotsuba.feature.history.HistoryScreen
import dev.stan.yotsuba.feature.media.MediaScreen
import dev.stan.yotsuba.feature.settings.SettingsScreen
import dev.stan.yotsuba.feature.settings.SettingsSectionScreen
import dev.stan.yotsuba.feature.thread.ThreadScreen
import dev.stan.yotsuba.feature.vault.VaultScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val expanded = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT

    fun navigateTopLevel(dest: TopLevelDestination) {
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val showChrome = TopLevelDestination.entries.any { dest ->
        currentDestination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
    }

    val navHost: @Composable (Modifier) -> Unit = { modifier ->
        NavHost(
            navController = navController,
            startDestination = Route.Boards,
            modifier = modifier,
        ) {
            composable<Route.Boards> {
                BoardsScreen(onOpenBoard = { navController.navigate(Route.Catalog(it)) })
            }
            composable<Route.Catalog> { entry ->
                val route = entry.toRoute<Route.Catalog>()
                CatalogScreen(
                    board = route.board,
                    initialSearch = route.searchQuery,
                    onBack = { navController.popBackStack() },
                    onOpenThread = { threadNo ->
                        navController.navigate(Route.Thread(route.board, threadNo))
                    },
                )
            }
            composable<Route.Thread> { entry ->
                val route = entry.toRoute<Route.Thread>()
                ThreadScreen(
                    board = route.board,
                    threadNo = route.threadNo,
                    scrollToPostNo = route.scrollToPostNo,
                    onBack = { navController.popBackStack() },
                    onOpenMedia = { postNo ->
                        navController.navigate(Route.Media(route.board, route.threadNo, postNo))
                    },
                    onOpenInternal = { link ->
                        when (link) {
                            is dev.stan.yotsuba.core.util.Urls.InternalLink.Catalog ->
                                navController.navigate(Route.Catalog(link.board, link.searchQuery))
                            is dev.stan.yotsuba.core.util.Urls.InternalLink.Thread ->
                                navController.navigate(Route.Thread(link.board, link.threadNo, link.postNo))
                        }
                    },
                )
            }
            composable<Route.Media> { entry ->
                val route = entry.toRoute<Route.Media>()
                MediaScreen(
                    board = route.board,
                    threadNo = route.threadNo,
                    initialPostNo = route.initialPostNo,
                    onClose = { navController.popBackStack() },
                )
            }
            composable<Route.Bookmarks> {
                BookmarksScreen(onOpenThread = { board, no ->
                    navController.navigate(Route.Thread(board, no))
                })
            }
            composable<Route.History> {
                HistoryScreen(onOpenThread = { board, no, post ->
                    navController.navigate(Route.Thread(board, no, post))
                })
            }
            composable<Route.Vault> {
                VaultScreen(onOpenThread = { board, no, post ->
                    navController.navigate(Route.Thread(board, no, post))
                })
            }
            composable<Route.Settings> {
                SettingsScreen(onOpenSection = { navController.navigate(Route.SettingsSection(it)) })
            }
            composable<Route.SettingsSection> { entry ->
                SettingsSectionScreen(
                    section = entry.toRoute<Route.SettingsSection>().id,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }

    // One items builder for both chrome variants; only the item composable differs.
    val navItems: @Composable (
        item: @Composable (Boolean, () -> Unit, @Composable () -> Unit, @Composable () -> Unit) -> Unit,
    ) -> Unit = { item ->
        TopLevelDestination.entries.forEach { dest ->
            val selected = currentDestination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
            item(
                selected,
                { navigateTopLevel(dest) },
                { Icon(if (selected) dest.selectedIcon else dest.unselectedIcon, contentDescription = null) },
                { Text(stringResource(dest.labelRes)) },
            )
        }
    }

    if (expanded && showChrome) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                navItems { selected, onClick, icon, label ->
                    NavigationRailItem(selected = selected, onClick = onClick, icon = icon, label = label)
                }
            }
            navHost(Modifier.weight(1f))
        }
    } else {
        Scaffold(
            bottomBar = {
                if (showChrome) {
                    NavigationBar {
                        navItems { selected, onClick, icon, label ->
                            NavigationBarItem(selected = selected, onClick = onClick, icon = icon, label = label)
                        }
                    }
                }
            },
        ) { padding ->
            navHost(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()))
        }
    }
}

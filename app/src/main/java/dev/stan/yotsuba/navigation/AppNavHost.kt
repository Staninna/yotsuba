package dev.stan.yotsuba.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.window.core.layout.WindowWidthSizeClass
import dev.stan.yotsuba.core.designsystem.component.TabScaffoldSlots
import dev.stan.yotsuba.core.util.Urls.InternalLink
import dev.stan.yotsuba.feature.boards.BoardsScreen
import dev.stan.yotsuba.feature.catalog.CatalogScreen
import dev.stan.yotsuba.feature.media.MediaScreen
import dev.stan.yotsuba.feature.settings.SettingsScreen
import dev.stan.yotsuba.feature.settings.SettingsSectionScreen
import dev.stan.yotsuba.feature.thread.ThreadScreen
import dev.stan.yotsuba.feature.threads.ThreadsScreen
import dev.stan.yotsuba.feature.vault.VaultScreen

/**
 * The app shell: one Scaffold, one bottom bar (or rail), one NavHost. Tab screens fill
 * the top bar and FAB through [TabScaffoldSlots]; pushed screens bring their own Scaffold
 * and the shell hides its chrome while they are on top.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val expanded = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT
    val slots = remember { TabScaffoldSlots() }

    fun navigateTopLevel(dest: TopLevelDestination) {
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun isSelected(dest: TopLevelDestination) =
        currentDestination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true

    val showChrome = TopLevelDestination.entries.any(::isSelected)
    val openSettings = { navController.navigate(Route.Settings) }

    Row(Modifier.fillMaxSize()) {
        if (expanded && showChrome) {
            NavigationRail {
                for (dest in TopLevelDestination.entries) {
                    val selected = isSelected(dest)
                    NavigationRailItem(
                        selected = selected,
                        onClick = { navigateTopLevel(dest) },
                        icon = { Icon(if (selected) dest.selectedIcon else dest.unselectedIcon, contentDescription = null) },
                        label = { Text(stringResource(dest.labelRes)) },
                    )
                }
            }
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            // Pushed screens own their insets; tab bars handle theirs. Nothing to add here.
            contentWindowInsets = WindowInsets(0.dp),
            topBar = { if (showChrome) slots.topBar() },
            floatingActionButton = { if (showChrome) slots.floatingActionButton() },
            snackbarHost = { SnackbarHost(slots.snackbar) },
            bottomBar = {
                if (!expanded && showChrome) {
                    NavigationBar {
                        for (dest in TopLevelDestination.entries) {
                            val selected = isSelected(dest)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest) },
                                icon = { Icon(if (selected) dest.selectedIcon else dest.unselectedIcon, contentDescription = null) },
                                label = { Text(stringResource(dest.labelRes)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Route.Boards,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                composable<Route.Boards> {
                    BoardsScreen(
                        slots = slots,
                        onOpenBoard = { navController.navigate(Route.Catalog(it)) },
                    )
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
                        onOpenInternal = { link -> navController.openInternal(link, from = route) },
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
                composable<Route.Threads> {
                    ThreadsScreen(
                        slots = slots,
                        onOpenThread = { board, no, post -> navController.navigate(Route.Thread(board, no, post)) },
                        onOpenSettings = openSettings,
                    )
                }
                composable<Route.Vault> {
                    VaultScreen(
                        slots = slots,
                        onOpenThread = { board, no, post ->
                            navController.navigate(Route.Thread(board, no, post))
                        },
                    )
                }
                composable<Route.Settings> {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSection = { navController.navigate(Route.SettingsSection(it)) },
                    )
                }
                composable<Route.SettingsSection> { entry ->
                    SettingsSectionScreen(
                        section = entry.toRoute<Route.SettingsSection>().id,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

/**
 * Follows a quote or board link from inside a thread without letting a chain of quotes
 * pile up entries:
 *
 * - a link to the thread already open just scrolls (single-top swaps the post argument);
 * - a link to a thread on a board whose catalog is on the stack pops back to that catalog
 *   first, so catalog -> A -> B -> C stays catalog -> C;
 * - a link to a catalog already on the stack pops back to it instead of pushing a twin;
 * - anything else pushes normally.
 */
private fun NavController.openInternal(link: InternalLink, from: Route.Thread) {
    when (link) {
        is InternalLink.Catalog -> {
            val onStack = link.searchQuery == null && popBackStack(Route.Catalog(link.board), inclusive = false)
            if (!onStack) navigate(Route.Catalog(link.board, link.searchQuery))
        }
        is InternalLink.Thread -> {
            val target = Route.Thread(link.board, link.threadNo, link.postNo)
            val catalog = Route.Catalog(link.board)
            when {
                link.board == from.board && link.threadNo == from.threadNo ->
                    navigate(target) { launchSingleTop = true }
                hasEntry(catalog) -> navigate(target) { popUpTo(catalog) { inclusive = false } }
                else -> navigate(target)
            }
        }
    }
}

private fun NavController.hasEntry(route: Route): Boolean =
    runCatching { getBackStackEntry(route) }.isSuccess

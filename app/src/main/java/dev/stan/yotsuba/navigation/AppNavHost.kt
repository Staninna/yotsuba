package dev.stan.yotsuba.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.navigation.NavDestination
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.window.core.layout.WindowWidthSizeClass
import dev.stan.yotsuba.core.designsystem.component.LocalAnimatedVisibilityScope
import dev.stan.yotsuba.core.designsystem.component.LocalSharedTransitionScope
import dev.stan.yotsuba.core.designsystem.component.TabScaffoldSlots
import dev.stan.yotsuba.core.designsystem.token.LocalMotion
import dev.stan.yotsuba.core.util.Urls.InternalLink
import dev.stan.yotsuba.core.widget.WidgetDeepLink
import dev.stan.yotsuba.feature.boards.BoardsScreen
import dev.stan.yotsuba.feature.home.HomeScreen
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
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavHost(shell: ShellViewModel = hiltViewModel()) {
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

    // Targets that arrive from outside the graph: a browser link, shared text, a widget tap.
    val pendingLink by shell.pendingLink.collectAsStateWithLifecycle()
    LaunchedEffect(pendingLink) {
        val link = pendingLink ?: return@LaunchedEffect
        when (link) {
            is InternalLink.Catalog -> navController.navigate(Route.Catalog(link.board, link.searchQuery))
            is InternalLink.Thread -> navController.navigate(Route.Thread(link.board, link.threadNo, link.postNo))
        }
        shell.linkConsumed()
    }
    val widgetTarget by WidgetDeepLink.pending.collectAsStateWithLifecycle()
    LaunchedEffect(widgetTarget) {
        val target = widgetTarget ?: return@LaunchedEffect
        navController.navigate(Route.Thread(target.board, target.threadNo))
        WidgetDeepLink.clear()
    }
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
            val motion = LocalMotion.current
            val fade = tween<Float>(motion.medium)
            val slide = tween<IntOffset>(motion.medium)
            // One shared-transition scope over the whole graph, so a thumbnail on one screen
            // and its viewer page on the next can morph into each other (Modifier.sharedMedia).
            SharedTransitionLayout(Modifier.fillMaxSize().padding(padding)) {
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    NavHost(
                        navController = navController,
                        startDestination = Route.Home,
                        modifier = Modifier.fillMaxSize(),
                        // A tab switch composes both screens at once, so it gets a short fade
                        // and no slide; the push/pop slide is for screens that stack.
                        enterTransition = {
                            if (isTabSwitch()) fadeIn(tween(motion.short))
                            else fadeIn(fade) + slideInHorizontally(slide) { it / 8 }
                        },
                        exitTransition = { fadeOut(tween(motion.short)) },
                        popEnterTransition = { fadeIn(if (isTabSwitch()) tween(motion.short) else fade) },
                        popExitTransition = {
                            if (isTabSwitch()) fadeOut(tween(motion.short))
                            else fadeOut(fade) + slideOutHorizontally(slide) { it / 8 }
                        },
                    ) {
                        screen<Route.Home> {
                            HomeScreen(
                                onOpenThread = { board, threadNo -> navController.navigate(Route.Thread(board, threadNo)) },
                                onOpenBoards = { navigateTopLevel(TopLevelDestination.BOARDS) },
                                onOpenSettings = openSettings,
                            )
                        }
                        screen<Route.Boards> {
                            BoardsScreen(
                                slots = slots,
                                onOpenBoard = { navController.navigate(Route.Catalog(it)) },
                                onOpenSettings = openSettings,
                            )
                        }
                        screen<Route.Catalog> { entry ->
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
                        screen<Route.Thread> { entry ->
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
                        screen<Route.Media> { entry ->
                            val route = entry.toRoute<Route.Media>()
                            MediaScreen(
                                board = route.board,
                                threadNo = route.threadNo,
                                initialPostNo = route.initialPostNo,
                                onClose = { navController.popBackStack() },
                            )
                        }
                        screen<Route.Threads> {
                            ThreadsScreen(
                                slots = slots,
                                onOpenThread = { board, no, post -> navController.navigate(Route.Thread(board, no, post)) },
                                onOpenSettings = openSettings,
                            )
                        }
                        screen<Route.Vault> {
                            VaultScreen(
                                slots = slots,
                                onOpenSettings = openSettings,
                                onOpenThread = { board, no, post ->
                                    navController.navigate(Route.Thread(board, no, post))
                                },
                            )
                        }
                        screen<Route.Settings> {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onOpenSection = { navController.navigate(Route.SettingsSection(it)) },
                            )
                        }
                        screen<Route.SettingsSection> { entry ->
                            SettingsSectionScreen(
                                section = entry.toRoute<Route.SettingsSection>().id,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A destination whose content can take part in shared-element transitions: the entry's
 * [AnimatedVisibilityScope] is published for [dev.stan.yotsuba.core.designsystem.component.sharedMedia].
 */
private inline fun <reified T : Any> NavGraphBuilder.screen(
    crossinline content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) { content(entry) }
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

private fun NavDestination.isTopLevel(): Boolean =
    TopLevelDestination.entries.any { hasRoute(it.route::class) }

/** Both ends of the transition are tab roots: a bottom-bar tap, not a push or a pop. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.isTopLevel() && targetState.destination.isTopLevel()

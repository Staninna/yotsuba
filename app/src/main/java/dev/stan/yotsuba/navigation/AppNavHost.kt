package dev.stan.yotsuba.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.navigation.NavDestination
import androidx.compose.animation.AnimatedContentTransitionScope
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import dev.stan.yotsuba.feature.media.findComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import dev.stan.yotsuba.core.designsystem.rememberNavTransitions
import dev.stan.yotsuba.core.util.Urls.InternalLink
import dev.stan.yotsuba.feature.boards.BoardsScreen
import dev.stan.yotsuba.feature.home.HomeScreen
import dev.stan.yotsuba.feature.catalog.CatalogScreen
import dev.stan.yotsuba.feature.catalog.ThreadSiblingsViewModel
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

    // A tab's own viewer (the vault's) can shrink the whole activity into picture-in-picture;
    // the bottom bar and top bar have no place in that window.
    val inPip = rememberInPictureInPictureMode()
    val showChrome = !inPip && TopLevelDestination.entries.any(::isSelected)

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
    val openSettings = { navController.push(Route.Settings) }

    Row(Modifier.fillMaxSize()) {
        if (expanded && showChrome) {
            NavigationRail {
                NavItems(::isSelected, ::navigateTopLevel) { selected, onClick, icon, label ->
                    NavigationRailItem(selected = selected, onClick = onClick, icon = icon, label = label)
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
                        NavItems(::isSelected, ::navigateTopLevel) { selected, onClick, icon, label ->
                            NavigationBarItem(selected = selected, onClick = onClick, icon = icon, label = label)
                        }
                    }
                }
            },
        ) { padding ->
            val transitions = rememberNavTransitions()
            // One shared-transition scope over the whole graph, so a thumbnail on one screen
            // and its viewer page on the next can morph into each other (Modifier.sharedMedia).
            SharedTransitionLayout(Modifier.fillMaxSize().padding(padding)) {
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    NavHost(
                        navController = navController,
                        startDestination = Route.Home,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = {
                            threadSwipeDirection()?.let { transitions.swipeEnter(it) }
                                ?: transitions.enter(tabSwitch = isTabSwitch())
                        },
                        exitTransition = {
                            threadSwipeDirection()?.let { transitions.swipeExit(it) }
                                ?: transitions.exit()
                        },
                        popEnterTransition = { transitions.popEnter(tabSwitch = isTabSwitch()) },
                        popExitTransition = { transitions.popExit(tabSwitch = isTabSwitch()) },
                    ) {
                        screen<Route.Home> {
                            HomeScreen(
                                slots = slots,
                                onOpenThread = { board, threadNo -> navController.push(Route.Thread(board, threadNo)) },
                                onOpenBoards = { navigateTopLevel(TopLevelDestination.BOARDS) },
                                onOpenSettings = openSettings,
                            )
                        }
                        screen<Route.Boards> {
                            BoardsScreen(
                                slots = slots,
                                onOpenBoard = { navController.push(Route.Catalog(it)) },
                                onOpenSettings = openSettings,
                            )
                        }
                        screen<Route.Catalog> { entry ->
                            val route = entry.toRoute<Route.Catalog>()
                            CatalogScreen(
                                board = route.board,
                                initialSearch = route.searchQuery,
                                onBack = { navController.popBackStack() },
                                onOpenThread = { threadNo -> navController.push(Route.Thread(route.board, threadNo)) },
                            )
                        }
                        screen<Route.Thread> { entry ->
                            val route = entry.toRoute<Route.Thread>()
                            val siblings = hiltViewModel<ThreadSiblingsViewModel>().store
                            ThreadScreen(
                                board = route.board,
                                threadNo = route.threadNo,
                                scrollToPostNo = route.scrollToPostNo,
                                onBack = { navController.popBackStack() },
                                onOpenMedia = { postNo -> navController.push(Route.Media(route.board, route.threadNo, postNo)) },
                                onOpenInternal = { link -> navController.openInternal(link, from = route) },
                                siblings = { siblings.neighbours(route.board, route.threadNo) },
                                onOpenSibling = { threadNo, forward ->
                                    navController.openSibling(Route.Thread(route.board, threadNo, swipeForward = forward))
                                },
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
                                onOpenThread = { board, no, post -> navController.push(Route.Thread(board, no, post)) },
                                onOpenSettings = openSettings,
                            )
                        }
                        screen<Route.Vault> {
                            VaultScreen(
                                slots = slots,
                                onOpenSettings = openSettings,
                                onOpenThread = { board, no, post -> navController.push(Route.Thread(board, no, post)) },
                            )
                        }
                        screen<Route.Settings> {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onOpenSection = { navController.push(Route.SettingsSection(it)) },
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

/** The tab list, once, for whichever container ([NavigationRail] or [NavigationBar]) is showing. */
@Composable
private fun NavItems(
    isSelected: (TopLevelDestination) -> Boolean,
    onSelect: (TopLevelDestination) -> Unit,
    item: @Composable (
        selected: Boolean,
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
        label: @Composable () -> Unit,
    ) -> Unit,
) {
    for (dest in TopLevelDestination.entries) {
        val selected = isSelected(dest)
        item(
            selected,
            { onSelect(dest) },
            { Icon(if (selected) dest.selectedIcon else dest.unselectedIcon, contentDescription = null) },
            { Text(stringResource(dest.labelRes)) },
        )
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
 * Pushes a screen. Single-top, so a double tap before the destination composes lands on
 * one screen instead of two stacked copies.
 */
private fun NavController.push(route: Route) = navigate(route) { launchSingleTop = true }

/**
 * Follows a quote or board link from inside a thread without letting a chain of quotes
 * pile up entries:
 *
 * - a link to the thread already open just scrolls (single-top swaps the post argument);
 * - a link to a thread on a board whose catalog is on the stack pops back to that catalog
 *   first, so catalog -> A -> B -> C stays catalog -> C (popUpTo a catalog that is not on
 *   the stack is a no-op, so anything else pushes normally);
 * - a link to a catalog already on the stack pops back to it instead of pushing a twin.
 */
private fun NavController.openInternal(link: InternalLink, from: Route.Thread) {
    when (link) {
        is InternalLink.Catalog -> {
            if (link.searchQuery != null) push(Route.Catalog(link.board, link.searchQuery))
            else if (!popBackStack(Route.Catalog(link.board), inclusive = false)) push(Route.Catalog(link.board))
        }
        is InternalLink.Thread -> {
            val target = Route.Thread(link.board, link.threadNo, link.postNo)
            if (link.board == from.board && link.threadNo == from.threadNo) push(target)
            else navigate(target) { popUpTo(Route.Catalog(link.board)) { inclusive = false } }
        }
    }
}

/**
 * Swaps the open thread for a catalog neighbour. The current entry goes, so back still lands
 * on the catalog (or wherever the thread was opened from) rather than on the thread swiped
 * away, and each swipe gets a fresh ViewModel for its own thread.
 */
private fun NavController.openSibling(target: Route.Thread) = navigate(target) {
    popUpTo<Route.Thread> { inclusive = true }
    launchSingleTop = true
}

/** Whether the hosting activity is currently in picture-in-picture, as Compose state. */
@Composable
private fun rememberInPictureInPictureMode(): Boolean {
    val activity = LocalContext.current.findComponentActivity() ?: return false
    var inPip by remember { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

private fun NavDestination.isTopLevel(): Boolean =
    TopLevelDestination.entries.any { hasRoute(it.route::class) }

/**
 * The direction of a thread-for-neighbour swap, read off the target entry's own arguments
 * so nothing outside the transition has to be set and cleared around it: true for
 * forward, false for back, null when this is not a swipe between two threads.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.threadSwipeDirection(): Boolean? {
    if (!initialState.destination.hasRoute<Route.Thread>()) return null
    if (!targetState.destination.hasRoute<Route.Thread>()) return null
    return targetState.toRoute<Route.Thread>().swipeForward
}

/** Both ends of the transition are tab roots: a bottom-bar tap, not a push or a pop. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.isTopLevel() && targetState.destination.isTopLevel()

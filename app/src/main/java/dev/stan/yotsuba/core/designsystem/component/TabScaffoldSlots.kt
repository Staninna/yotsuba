package dev.stan.yotsuba.core.designsystem.component

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * What a tab screen contributes to the one app-level Scaffold: its top bar, its FAB and
 * the snackbar host it reports through. The shell owns the Scaffold and the bottom bar;
 * a tab fills these in with [TabChrome] instead of nesting a Scaffold of its own.
 */
@Stable
class TabScaffoldSlots {
    var topBar: @Composable () -> Unit by mutableStateOf({})
        internal set
    var floatingActionButton: @Composable () -> Unit by mutableStateOf({})
        internal set
    val snackbar = SnackbarHostState()

    /** The [TabChrome] currently filling the slots, so a leaving tab cannot wipe the next one's. */
    internal var owner: Any? = null
}

/**
 * Publishes [topBar] and [floatingActionButton] into [slots] for as long as the caller
 * is composed. During a tab switch both screens are briefly alive; the newest caller
 * wins and the old one's disposal leaves the slots alone.
 */
@Composable
fun TabChrome(
    slots: TabScaffoldSlots,
    topBar: @Composable () -> Unit,
    floatingActionButton: @Composable () -> Unit = {},
) {
    val token = remember { Any() }
    // The slot holds one stable lambda per caller that reads the latest content through
    // rememberUpdatedState; assigning a fresh lambda on every recomposition would invalidate
    // the outer Scaffold's top bar each time anything in the tab recomposed.
    val currentTopBar by rememberUpdatedState(topBar)
    val currentFab by rememberUpdatedState(floatingActionButton)
    val stableTopBar = remember<@Composable () -> Unit> { { currentTopBar() } }
    val stableFab = remember<@Composable () -> Unit> { { currentFab() } }
    // Claim and release in the same effect: the claim runs once per mount, in composition
    // apply order, so the later-mounting tab really is the newest owner. A SideEffect here
    // would re-claim on every recomposition of the leaving tab and steal the slots back.
    DisposableEffect(slots, token) {
        slots.owner = token
        slots.topBar = stableTopBar
        slots.floatingActionButton = stableFab
        onDispose {
            if (slots.owner === token) {
                slots.owner = null
                slots.topBar = {}
                slots.floatingActionButton = {}
            }
        }
    }
}

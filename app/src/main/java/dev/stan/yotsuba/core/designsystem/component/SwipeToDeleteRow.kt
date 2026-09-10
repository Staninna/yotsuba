package dev.stan.yotsuba.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.stan.yotsuba.core.designsystem.rememberHaptics

/** Swipe-to-dismiss row with the shared red delete background. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Committing takes a drag to 75% of the width; onDismiss only fires
    // after the finger lifts and the row settles off-screen.
    val haptics = rememberHaptics()
    // remember, not rememberSaveable: a lazy list holds an item's saveable state under its
    // key, so a row that an undo puts back came back already dismissed and the box fired
    // onDismiss as it composed, deleting the row a second time and losing the undo. A
    // freshly composed row gets a freshly settled state, and a half-swiped row that
    // survives a configuration change settles back rather than deleting itself.
    val dismissState = remember {
        SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            positionalThreshold = { totalDistance -> totalDistance * 0.75f },
        )
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeDeleteBackground(dismissState) },
        onDismiss = { haptics.confirm(); onDelete() },
        modifier = modifier,
    ) {
        content()
    }
}

/** Shows [message] with an undo action; runs [onUndo] when the action is tapped. */
suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String, onUndo: () -> Unit) {
    if (showSnackbar(message, actionLabel = undoLabel) == SnackbarResult.ActionPerformed) onUndo()
}

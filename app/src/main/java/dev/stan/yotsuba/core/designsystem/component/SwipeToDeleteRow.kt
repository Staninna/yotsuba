package dev.stan.yotsuba.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.75f },
    )
    // A lazy list holds an item's saveable state under its key, and this state is saveable,
    // so a row that an undo puts back came back already dismissed and the box fired onDismiss
    // again as it composed: the row deleted itself a second time and the undo lost the item.
    // Every freshly composed row starts settled. A real swipe is unaffected, since it runs
    // once per composition and a genuinely dismissed row is on its way out anyway.
    LaunchedEffect(Unit) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
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

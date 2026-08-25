package dev.stan.yotsuba.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.75f },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeDeleteBackground(dismissState) },
        onDismiss = { onDelete() },
        modifier = modifier,
    ) {
        content()
    }
}

/** Shows [message] with an undo action; runs [onUndo] when the action is tapped. */
suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String, onUndo: () -> Unit) {
    if (showSnackbar(message, actionLabel = undoLabel) == SnackbarResult.ActionPerformed) onUndo()
}

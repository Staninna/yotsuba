package dev.stan.yotsuba.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.rememberMotionSpec
import dev.stan.yotsuba.core.designsystem.token.LocalMotion
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing

/**
 * Red delete background for [androidx.compose.material3.SwipeToDismissBox] rows:
 * container red while dragging, full error red plus a slightly grown icon once the
 * release would commit the delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeDeleteBackground(state: SwipeToDismissBoxState) {
    val spacing = LocalSpacing.current
    val committing = state.targetValue != SwipeToDismissBoxValue.Settled
    val motion = LocalMotion.current
    val color by animateColorAsState(
        if (committing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer,
        animationSpec = rememberMotionSpec(motion.short),
        label = "swipeDeleteColor",
    )
    val iconScale by animateFloatAsState(
        if (committing) 1.25f else 1f,
        animationSpec = rememberMotionSpec(motion.short),
        label = "swipeDeleteScale",
    )
    val alignment = when (state.dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        else -> Alignment.CenterEnd
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(color, MaterialTheme.shapes.medium)
            .padding(horizontal = spacing.lg),
        contentAlignment = alignment,
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = stringResource(R.string.action_remove),
            tint = if (committing) {
                MaterialTheme.colorScheme.onError
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
            modifier = Modifier.scale(iconScale),
        )
    }
}

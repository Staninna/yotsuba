package dev.stan.yotsuba.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.TimeFormat

/**
 * The overlay a grid tile wears when it is a video: a centred play icon and, when known,
 * the duration in the bottom-right corner. Call inside the tile's [androidx.compose.foundation.layout.Box].
 */
@Composable
fun BoxScope.VideoBadge(durationMs: Long? = null) {
    val spacing = LocalSpacing.current
    Icon(
        Icons.Filled.PlayCircle,
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.85f),
        modifier = Modifier.align(Alignment.Center).size(spacing.xxl),
    )
    durationMs?.let { duration ->
        Text(
            TimeFormat.duration(duration),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(spacing.xs)
                .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = spacing.xs, vertical = 1.dp),
        )
    }
}

package dev.stan.yotsuba.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing

/**
 * Thumbnail + title/excerpt/metadata summary of a thread, used by list rows
 * (bookmarks, history). Badges and pills go in [trailing].
 */
@Composable
fun ThreadSummaryRow(
    thumbnailUrl: String?,
    title: String,
    metadata: String,
    modifier: Modifier = Modifier,
    excerpt: String? = null,
    thumbnailSize: Dp = 64.dp,
    titleColor: Color = Color.Unspecified,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    Row(Modifier.padding(spacing.md).then(modifier), verticalAlignment = Alignment.CenterVertically) {
        MediaThumbnail(
            url = thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(thumbnailSize),
        )
        Spacer(Modifier.width(spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = titleColor,
            )
            excerpt?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                metadata,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke(this)
    }
}

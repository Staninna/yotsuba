package dev.stan.yotsuba.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * A thumbnail that renders the spoiler tile (D22) instead of the image until revealed,
 * and a named placeholder for deleted files.
 */
@Composable
fun MediaThumbnail(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    spoilered: Boolean = false,
    deleted: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop,
) {
    when {
        deleted || url.isNullOrEmpty() -> Box(
            modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.HideImage,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        spoilered -> Box(
            modifier.background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.VisibilityOff,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        else -> AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

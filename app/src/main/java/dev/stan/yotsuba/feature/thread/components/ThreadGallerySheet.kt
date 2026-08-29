package dev.stan.yotsuba.feature.thread.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.MediaThumbnail
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.domain.model.ThreadPost

/** Every attachment in the thread as a grid; tapping one opens the viewer at that post. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadGallerySheet(
    posts: List<ThreadPost>,
    revealAll: Boolean,
    onOpen: (ThreadPost) -> Unit,
    onSaveAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.thread_gallery_title, posts.size, posts.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSaveAll, enabled = posts.isNotEmpty()) {
                    Text(stringResource(R.string.thread_gallery_save_all))
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                contentPadding = PaddingValues(bottom = spacing.lg),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(posts, key = { it.no }) { post ->
                    val media = post.presentMedia ?: return@items
                    MediaThumbnail(
                        url = media.thumbnailUrl,
                        contentDescription = media.displayName,
                        spoilered = media.spoiler && !revealAll,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { onOpen(post) },
                    )
                }
            }
        }
    }
}

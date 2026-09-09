package dev.stan.yotsuba.feature.thread.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.EmptyState
import dev.stan.yotsuba.core.designsystem.component.MediaThumbnail
import dev.stan.yotsuba.core.designsystem.component.VideoBadge
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.ThreadPost

/** Which attachments the gallery shows; the chip row picks one. */
enum class GalleryFilter(val labelRes: Int) {
    ALL(R.string.thread_gallery_filter_all),
    IMAGES(R.string.thread_gallery_filter_images),
    VIDEOS(R.string.thread_gallery_filter_videos),
    WITH_SOUND(R.string.thread_gallery_filter_with_sound),
    SILENT(R.string.thread_gallery_filter_silent),
    ;

    fun matches(item: MediaItem, boardAllowsAudio: Boolean): Boolean = when (this) {
        ALL -> true
        IMAGES -> !item.isVideo
        VIDEOS -> item.isVideo
        WITH_SOUND -> item.mayHaveSound(boardAllowsAudio)
        SILENT -> item.isVideo && !item.mayHaveSound(boardAllowsAudio)
    }
}

/** One grid cell: the first post carrying a file, and how many posts in the thread carry it. */
data class GalleryTile(val post: ThreadPost, val media: MediaItem, val copies: Int)

/**
 * The cells for [posts] under [filter], in thread order, one per distinct file: reposts
 * (same MD5) fold into the first post that carried the file, which shows the count. A file
 * whose source gave no MD5 stands alone.
 */
internal fun galleryTiles(posts: List<ThreadPost>, filter: GalleryFilter, boardAllowsAudio: Boolean): List<GalleryTile> =
    posts.mapNotNull { post -> post.presentMedia?.let { post to it } }
        .filter { (_, media) -> filter.matches(media, boardAllowsAudio) }
        .groupBy { (_, media) -> media.md5 ?: media.fullUrl }
        .values.map { copies -> GalleryTile(copies.first().first, copies.first().second, copies.size) }

/**
 * Every attachment in the thread as a grid, narrowed by a chip row; tapping one opens the
 * viewer at that post. [onSaveAll] receives the posts currently shown, not the whole thread.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadGallerySheet(
    posts: List<ThreadPost>,
    revealAll: Boolean,
    boardAllowsAudio: Boolean,
    onOpen: (ThreadPost) -> Unit,
    onSaveAll: (List<ThreadPost>) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var filter by rememberSaveable { mutableStateOf(GalleryFilter.ALL) }
    val withMedia = remember(posts, filter, boardAllowsAudio) { galleryTiles(posts, filter, boardAllowsAudio) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.thread_gallery_title, withMedia.size, withMedia.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onSaveAll(withMedia.map { it.post }) }, enabled = withMedia.isNotEmpty()) {
                    Text(stringResource(R.string.thread_gallery_save_all))
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                GalleryFilter.entries.forEach { option ->
                    FilterChip(
                        selected = option == filter,
                        onClick = { filter = option },
                        label = { Text(stringResource(option.labelRes)) },
                    )
                }
            }
            if (boardAllowsAudio) {
                Text(
                    stringResource(R.string.thread_gallery_sound_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = spacing.xs),
                )
            }
            if (withMedia.isEmpty()) {
                EmptyState(
                    title = stringResource(
                        if (filter == GalleryFilter.ALL) R.string.thread_gallery_empty
                        else R.string.thread_gallery_filter_empty,
                    ),
                    explanation = stringResource(
                        if (filter == GalleryFilter.ALL) R.string.thread_gallery_empty_explanation
                        else R.string.thread_gallery_filter_empty_explanation,
                    ),
                    icon = Icons.Filled.Image,
                    modifier = Modifier.height(240.dp),
                )
                return@Column
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                contentPadding = PaddingValues(bottom = spacing.lg),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(withMedia, key = { it.post.no }) { (post, media, copies) ->
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clickable { onOpen(post) },
                    ) {
                        MediaThumbnail(
                            url = media.thumbnailUrl,
                            contentDescription = media.displayName,
                            spoilered = media.spoiler && !revealAll,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (media.isVideo) VideoBadge()
                        if (copies > 1) {
                            Text(
                                stringResource(R.string.thread_gallery_copies, copies),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(spacing.xs)
                                    .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall)
                                    .padding(horizontal = spacing.xs, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

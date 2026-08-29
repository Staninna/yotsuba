package dev.stan.yotsuba.feature.thread.components

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.FileSize
import dev.stan.yotsuba.core.util.TimeFormat
import dev.stan.yotsuba.core.designsystem.component.MediaThumbnail
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.ThreadPost

/** Deterministic chip colour from the poster-ID hash, harmonised into the scheme (D21). */
fun posterIdColor(id: String, dark: Boolean): Color {
    val hue = (id.hashCode().toUInt() % 360u).toFloat()
    return Color.hsv(hue, if (dark) 0.45f else 0.35f, if (dark) 0.45f else 0.85f)
}

/** ISO country code -> Unicode regional-indicator flag (D21). */
fun countryFlagEmoji(iso: String): String =
    iso.uppercase().filter { it in 'A'..'Z' }.map { 0x1F1E6 + (it - 'A') }
        .joinToString("") { String(Character.toChars(it)) }
        .ifEmpty { "🏳" }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostCard(
    post: ThreadPost,
    board: Board?,
    backlinkCount: Int,
    revealedSpoilerIds: Set<Int>,
    revealAll: Boolean,
    imageSpoilerRevealed: Boolean,
    darkTheme: Boolean,
    onBodyTap: (BodyTap) -> Unit,
    onThumbnailTap: () -> Unit,
    onThumbnailLongPress: () -> Unit = {},
    saveStatus: MediaSaveStatus? = null,
    onBacklinksTap: () -> Unit,
    onCopyPostNo: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: String? = null,
) {
    val spacing = LocalSpacing.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = if (post.isOp) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(spacing.md)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    post.name + (post.tripcode?.let { " $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (post.capcode != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                post.capcode?.let {
                    Spacer(Modifier.width(spacing.xs))
                    Text("## $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
                if (board?.userIds == true && post.posterId != null) {
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        post.posterId,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .background(posterIdColor(post.posterId, darkTheme), CircleShape)
                            .padding(horizontal = spacing.sm, vertical = 1.dp),
                    )
                }
                if ((board?.countryFlags == true || board?.boardFlags == true) && post.countryCode != null) {
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        countryFlagEmoji(post.countryCode),
                        modifier = Modifier.semantics {
                            contentDescription = post.countryName ?: post.countryCode
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    TimeFormat.relative(post.timeSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(spacing.sm))
                Text(
                    "#${post.no}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onCopyPostNo),
                )
            }
            post.subject?.let {
                Spacer(Modifier.height(spacing.xs))
                Text(it, style = MaterialTheme.typography.titleMedium)
            }
            post.media?.let { attachment ->
                Spacer(Modifier.height(spacing.sm))
                when (attachment) {
                    is PostMedia.Deleted -> Text(
                        stringResource(R.string.thread_file_deleted, attachment.displayName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is PostMedia.Present -> {
                        val media = attachment.item
                        Row {
                            Box {
                                MediaThumbnail(
                                    url = media.thumbnailUrl,
                                    contentDescription = stringResource(
                                        R.string.media_image_description,
                                        media.displayName, media.width, media.height,
                                    ),
                                    spoilered = media.spoiler && !revealAll && !imageSpoilerRevealed,
                                    modifier = Modifier
                                        .size(if (post.isOp) 140.dp else 100.dp)
                                        .combinedClickable(
                                            onClick = onThumbnailTap,
                                            onLongClick = onThumbnailLongPress,
                                        ),
                                )
                                saveStatus?.let { SaveStatusBadge(it, Modifier.align(Alignment.BottomEnd)) }
                            }
                            Spacer(Modifier.width(spacing.md))
                            Column {
                                Text(
                                    media.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                Text(
                                    "${FileSize.format(media.sizeBytes)} · ${media.width}×${media.height}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (post.body.segments.isNotEmpty()) {
                Spacer(Modifier.height(spacing.sm))
                PostBody(
                    body = post.body,
                    revealedSpoilerIds = revealedSpoilerIds,
                    revealAll = revealAll,
                    onTap = onBodyTap,
                    highlight = highlight,
                )
            }
            if (backlinkCount > 0) {
                Spacer(Modifier.height(spacing.xs))
                AssistChip(
                    onClick = onBacklinksTap,
                    label = {
                        Text(pluralStringResource(R.plurals.thread_replies_chip, backlinkCount, backlinkCount))
                    },
                )
            }
        }
    }
}

/** Tiny vault-status badge overlaid on a post thumbnail's corner. */
@Composable
private fun SaveStatusBadge(status: MediaSaveStatus, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(3.dp)
            .size(18.dp)
            .background(Color.Black.copy(alpha = 0.65f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            MediaSaveStatus.SAVED -> Icon(
                Icons.Filled.DownloadDone, stringResource(R.string.media_downloaded),
                tint = Color(0xFF81C784), modifier = Modifier.size(13.dp),
            )
            MediaSaveStatus.QUEUED -> Icon(
                Icons.Filled.Schedule, stringResource(R.string.media_queued),
                tint = Color.White, modifier = Modifier.size(13.dp),
            )
            MediaSaveStatus.DOWNLOADING -> CircularProgressIndicator(
                modifier = Modifier.size(11.dp), color = Color.White, strokeWidth = 1.5.dp,
            )
            MediaSaveStatus.FAILED -> Icon(
                Icons.Filled.ErrorOutline, stringResource(R.string.media_save_failed),
                tint = Color(0xFFE57373), modifier = Modifier.size(13.dp),
            )
        }
    }
}

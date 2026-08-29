package dev.stan.yotsuba.feature.thread.components

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.theme.LocalYotsubaColors
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.FileSize
import dev.stan.yotsuba.core.util.TimeFormat
import dev.stan.yotsuba.core.designsystem.component.MediaThumbnail
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.feature.thread.PostUiState

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

/**
 * Everything a post card can do. Nullable handlers are unsupported in that context and
 * the card renders them inert; [forPreview] is the one place that decides which.
 */
data class PostCardActions(
    val onBodyTap: (BodyTap) -> Unit,
    /** A held quotelink; the tap on it jumps, the hold previews. */
    val onBodyLongPress: ((BodyTap) -> Unit)? = null,
    val onThumbnailTap: () -> Unit,
    val onThumbnailLongPress: (() -> Unit)?,
    /** Tap on one number in the "quoted by" row. When null the row is not shown. */
    val onBacklinkTap: ((Long) -> Unit)?,
    /** Legacy count chip, shown only when [onBacklinkTap] is null (the media viewer's panel). */
    val onBacklinksTap: (() -> Unit)? = null,
    val onCopyPostNo: (() -> Unit)?,
    /** Tap on the poster-ID pill filters the thread to that ID. */
    val onPosterIdTap: (() -> Unit)? = null,
    /** Long-press anywhere on the card that is not itself a control: the post action sheet. */
    val onLongPress: (() -> Unit)? = null,
) {
    /** A card inside the quote preview overlay: read-only apart from following links. */
    fun forPreview(): PostCardActions = copy(
        onBodyLongPress = null, onThumbnailLongPress = null,
        onBacklinkTap = null, onBacklinksTap = null, onCopyPostNo = null, onPosterIdTap = null,
        onLongPress = null,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostCard(
    post: ThreadPost,
    board: Board?,
    ui: PostUiState,
    revealAll: Boolean,
    darkTheme: Boolean,
    actions: PostCardActions,
    modifier: Modifier = Modifier,
    highlight: String? = null,
    quoteLabels: Map<Long, String> = emptyMap(),
) {
    val spacing = LocalSpacing.current
    val onLongPress = actions.onLongPress
    val longPress = if (onLongPress == null) Modifier else Modifier.pointerInput(onLongPress) {
        detectTapGestures(onLongPress = { onLongPress() })
    }
    Card(
        modifier = modifier.fillMaxWidth().then(longPress),
        colors = when {
            ui.highlighted -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            post.isOp -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            else -> CardDefaults.cardColors()
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
                    val onPosterIdTap = actions.onPosterIdTap
                    Text(
                        if (ui.posterIdCount > 1) {
                            pluralStringResource(R.plurals.thread_poster_id_count, ui.posterIdCount, post.posterId, ui.posterIdCount)
                        } else post.posterId,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .background(posterIdColor(post.posterId, darkTheme), CircleShape)
                            .then(if (onPosterIdTap != null) Modifier.clickable(onClick = onPosterIdTap) else Modifier)
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
                    modifier = actions.onCopyPostNo?.let { Modifier.clickable(onClick = it) } ?: Modifier,
                )
            }
            if (ui.sticky || ui.closed) {
                Spacer(Modifier.height(spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    if (ui.sticky) ThreadBadge(Icons.Filled.PushPin, stringResource(R.string.thread_sticky))
                    if (ui.closed) ThreadBadge(Icons.Filled.Lock, stringResource(R.string.thread_closed))
                }
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
                                    spoilered = media.spoiler && !revealAll && !ui.imageSpoilerRevealed,
                                    modifier = Modifier
                                        .size(if (post.isOp) 140.dp else 100.dp)
                                        .combinedClickable(
                                            onClick = actions.onThumbnailTap,
                                            onLongClick = actions.onThumbnailLongPress,
                                        ),
                                )
                                ui.saveStatus?.let { SaveStatusBadge(it, Modifier.align(Alignment.BottomEnd)) }
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
                    revealedSpoilerIds = ui.revealedSpoilerIds,
                    revealAll = revealAll,
                    onTap = actions.onBodyTap,
                    highlight = highlight,
                    onLongPress = actions.onBodyLongPress,
                    quoteLabels = quoteLabels,
                )
            }
            val onBacklinkTap = actions.onBacklinkTap
            val onBacklinksTap = actions.onBacklinksTap
            val backlinkCount = ui.backlinks.size
            if (backlinkCount > 0 && onBacklinkTap != null) {
                Spacer(Modifier.height(spacing.xs))
                QuotedByRow(ui.backlinks, onBacklinkTap)
            } else if (backlinkCount > 0 && onBacklinksTap != null) {
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

/** "Closed" / "Sticky" on the OP card. */
@Composable
private fun ThreadBadge(icon: ImageVector, label: String) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .padding(horizontal = spacing.sm, vertical = 2.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(spacing.xs))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** "Quoted by: >>1 >>2" — each number jumps to that post. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuotedByRow(backlinks: List<Long>, onTap: (Long) -> Unit) {
    val spacing = LocalSpacing.current
    val colors = LocalYotsubaColors.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            stringResource(R.string.thread_quoted_by),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        backlinks.forEach { no ->
            Text(
                ">>$no",
                style = MaterialTheme.typography.labelSmall,
                color = colors.quotelink,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onTap(no) },
            )
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
            MediaSaveStatus.Saved -> Icon(
                Icons.Filled.DownloadDone, stringResource(R.string.media_downloaded),
                tint = Color(0xFF81C784), modifier = Modifier.size(13.dp),
            )
            MediaSaveStatus.Queued -> Icon(
                Icons.Filled.Schedule, stringResource(R.string.media_queued),
                tint = Color.White, modifier = Modifier.size(13.dp),
            )
            MediaSaveStatus.Downloading -> CircularProgressIndicator(
                modifier = Modifier.size(11.dp), color = Color.White, strokeWidth = 1.5.dp,
            )
            is MediaSaveStatus.Failed -> Icon(
                Icons.Filled.ErrorOutline, stringResource(R.string.media_save_failed),
                tint = Color(0xFFE57373), modifier = Modifier.size(13.dp),
            )
            is MediaSaveStatus.AlreadySaved -> Icon(
                Icons.Filled.DownloadDone, stringResource(R.string.media_already_saved),
                tint = Color(0xFF81C784).copy(alpha = 0.7f), modifier = Modifier.size(13.dp),
            )
        }
    }
}

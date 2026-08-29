package dev.stan.yotsuba.feature.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.FileSize
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState

/**
 * The shared top bar of every full-screen media viewer (live thread and vault): close
 * button, file name + info line, then viewer-specific [actions] on the right.
 */
@Composable
fun ViewerTopChrome(
    visible: Boolean,
    title: String,
    subtitle: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val spacing = LocalSpacing.current
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .statusBarsPadding()
                .padding(spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, stringResource(R.string.media_close), tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                    subtitle?.let {
                        Text(
                            it,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
                actions()
            }
        }
    }
}

/** Enter picture-in-picture, identical in both viewers. */
@Composable
fun PipButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Filled.PictureInPictureAlt,
            stringResource(R.string.media_pip),
            tint = Color.White,
        )
    }
}

/** Loop ⇄ auto-advance toggle, identical in both viewers. */
@Composable
fun AutoAdvanceButton(autoAdvance: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (autoAdvance) Icons.Filled.SkipNext else Icons.Filled.RepeatOne,
            stringResource(
                if (autoAdvance) R.string.vault_auto_advance_on else R.string.vault_auto_advance_off,
            ),
            tint = Color.White,
        )
    }
}

/**
 * The badge that survives the chrome fading out: while saves are still running, a small
 * pill keeps the count on screen. The top bar carries the same number in its subtitle, so
 * this only shows once that bar is gone -- a download in flight never goes silent.
 */
@Composable
fun DownloadIndicator(count: Int, visible: Boolean, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    val label = pluralStringResource(R.plurals.media_downloading, count, count)
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .padding(spacing.md)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                .padding(horizontal = spacing.sm, vertical = spacing.xs)
                .semantics { contentDescription = label },
        ) {
            CircularProgressIndicator(
                Modifier.size(14.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
            Text(
                count.toString(),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = spacing.xs),
            )
        }
    }
}

/**
 * One zoomable image page. [thumbnailModel] (usually the already-cached thumbnail) sits
 * underneath until the full image draws, so swiping never lands on a black page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImagePage(
    model: Any?,
    thumbnailModel: Any?,
    contentDescription: String?,
    onTap: () -> Unit,
    onLongPress: () -> Unit = {},
    /** Data saver: hold the full image behind a "Load (N MB)" tap over the thumbnail. */
    deferLoad: Boolean = false,
    sizeBytes: Long? = null,
) {
    val zoomState = rememberZoomableImageState()
    // Once tapped the image stays loaded for this page's lifetime, even if the connection
    // flips back to metered mid-thread: the bytes are already spent.
    var loadRequested by remember(model) { mutableStateOf(false) }
    val deferred = deferLoad && !loadRequested
    Box(Modifier.fillMaxSize()) {
        if (thumbnailModel != null && (deferred || !zoomState.isImageDisplayed)) {
            AsyncImage(
                model = thumbnailModel,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (deferred) {
            Box(
                Modifier
                    .fillMaxSize()
                    .combinedClickable(onClick = onTap, onLongClick = onLongPress),
                contentAlignment = Alignment.Center,
            ) {
                LoadPill(sizeBytes = sizeBytes, onClick = { loadRequested = true })
            }
        } else {
            ZoomableAsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                state = zoomState,
                onClick = { onTap() },
                onLongClick = { onLongPress() },
            )
        }
    }
}

/** "Load (1.2 MB)" over a deferred image; the size is dropped when it is unknown. */
@Composable
private fun LoadPill(sizeBytes: Long?, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    val label = sizeBytes?.let { stringResource(R.string.media_load_sized, FileSize.format(it)) }
        ?: stringResource(R.string.media_load)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Icon(Icons.Filled.Download, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = spacing.xs),
        )
    }
}

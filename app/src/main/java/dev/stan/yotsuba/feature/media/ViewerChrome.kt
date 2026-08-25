package dev.stan.yotsuba.feature.media

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
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
 * One zoomable image page. [thumbnailModel] (usually the already-cached thumbnail) sits
 * underneath until the full image draws, so swiping never lands on a black page.
 */
@Composable
fun ImagePage(
    model: Any?,
    thumbnailModel: Any?,
    contentDescription: String?,
    onTap: () -> Unit,
) {
    val zoomState = rememberZoomableImageState()
    Box(Modifier.fillMaxSize()) {
        if (thumbnailModel != null && !zoomState.isImageDisplayed) {
            AsyncImage(
                model = thumbnailModel,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        ZoomableAsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            state = zoomState,
            onClick = { onTap() },
        )
    }
}

tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

fun mimeOf(ext: String): String = when (ext.lowercase()) {
    ".jpg", ".jpeg" -> "image/jpeg"
    ".png" -> "image/png"
    ".gif" -> "image/gif"
    ".webp" -> "image/webp"
    ".webm" -> "video/webm"
    ".mp4" -> "video/mp4"
    else -> "application/octet-stream"
}

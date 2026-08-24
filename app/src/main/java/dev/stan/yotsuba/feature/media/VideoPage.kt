package dev.stan.yotsuba.feature.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil3.compose.AsyncImage
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import kotlinx.coroutines.delay
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

/**
 * One full-screen looping video page for a vertical media feed. Plays [videoUri]
 * (remote URL or local file/content URI); [thumbnailModel] stands in until the first frame.
 * The surface letterboxes — never crops — whatever the orientation.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPage(
    videoUri: String,
    thumbnailModel: Any?,
    initialWidth: Int,
    initialHeight: Int,
    selected: Boolean,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    muted: Boolean,
    chromeVisible: Boolean,
    onToggleMute: () -> Unit,
    onToggleChrome: () -> Unit,
    /** When true the video plays once and fires [onEnded]; otherwise it loops. */
    autoAdvance: Boolean = false,
    onEnded: () -> Unit = {},
) {
    val context = LocalContext.current
    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(videoUri))
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            prepare()
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var firstFrameRendered by remember(videoUri) { mutableStateOf(false) }
    var aspect by remember(videoUri) {
        mutableFloatStateOf(
            if (initialWidth > 0 && initialHeight > 0) initialWidth.toFloat() / initialHeight else 16f / 9f,
        )
    }

    LaunchedEffect(selected, playing) { player.playWhenReady = selected && playing }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(autoAdvance) {
        player.repeatMode = if (autoAdvance) ExoPlayer.REPEAT_MODE_OFF else ExoPlayer.REPEAT_MODE_ONE
    }
    // Closing the app or the PiP window stops the activity — audio must not keep running.
    val lifecycleOwner = LocalLifecycleOwner.current
    val shouldPlay = rememberUpdatedState(selected && playing)
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.playWhenReady = false
                Lifecycle.Event.ON_START -> player.playWhenReady = shouldPlay.value
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(videoUri) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
            isPlaying = player.isPlaying
            delay(250)
        }
    }
    val autoAdvanceNow = rememberUpdatedState(autoAdvance)
    val onEndedNow = rememberUpdatedState(onEnded)
    val selectedNow = rememberUpdatedState(selected)
    DisposableEffect(videoUri) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && autoAdvanceNow.value && selectedNow.value) {
                    onEndedNow.value()
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val zoomState = rememberZoomableState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            // Pinch-to-zoom over the whole video area; single taps still toggle the chrome.
            Modifier
                .matchParentSize()
                .zoomable(zoomState, onClick = { onToggleChrome() }),
            contentAlignment = Alignment.Center,
        ) {
            // Bare aspectRatio picks the largest size that satisfies BOTH constraints, so the
            // video letterboxes in landscape instead of filling the width and cropping.
            // TextureView (not the SurfaceView default) is required for zoom transforms to
            // actually render scaled.
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier = Modifier.aspectRatio(aspect),
            )
            // Thumbnail stands in until the first video frame is on screen, so swiping to a
            // video shows a preview instead of a black page while it loads.
            if (!firstFrameRendered) {
                AsyncImage(
                    model = thumbnailModel,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.aspectRatio(aspect),
                )
            }
        }
        if (!firstFrameRendered) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val spacing = LocalSpacing.current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .navigationBarsPadding()
                    .padding(horizontal = spacing.sm),
            ) {
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        stringResource(if (isPlaying) R.string.media_pause else R.string.media_play),
                        tint = Color.White,
                    )
                }
                Text(
                    formatMs(positionMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
                Slider(
                    value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                    onValueChange = { f ->
                        if (durationMs > 0) {
                            val target = (f * durationMs).toLong()
                            positionMs = target
                            player.seekTo(target)
                        }
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = spacing.sm),
                )
                Text(
                    formatMs(durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
                IconButton(onClick = onToggleMute) {
                    Icon(
                        if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        stringResource(if (muted) R.string.media_unmute else R.string.media_mute),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

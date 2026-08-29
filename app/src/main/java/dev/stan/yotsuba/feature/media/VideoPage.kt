package dev.stan.yotsuba.feature.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil3.compose.AsyncImage
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.sharedMedia
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import dev.stan.yotsuba.core.designsystem.token.LocalMotion
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomableState
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
    /** Shared-element key of the thumbnail this page was opened from; see [ImagePage]. */
    sharedKey: String? = null,
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
    behaviour: ViewerBehaviour = ViewerBehaviour(),
    onLongPress: () -> Unit = {},
    /** Any press on the transport bar; the owner uses it to keep the chrome awake. */
    onControlTouched: () -> Unit = {},
    /** True from the first drag of the seek bar until it is released. */
    onScrubbing: (Boolean) -> Unit = {},
    /** A sound post's external audio, kept in step with the video. */
    soundUrl: String? = null,
) {
    val context = LocalContext.current
    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(videoUri))
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            prepare()
        }
    }
    val soundPlayer = rememberSoundPlayer(soundUrl)
    DisposableEffect(player, soundPlayer) {
        val listener = soundPlayer?.followVisual(player)
        onDispose { listener?.let(player::removeListener) }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var firstFrameRendered by remember(videoUri) { mutableStateOf(false) }
    var buffering by remember(videoUri) { mutableStateOf(true) }
    // A failed webm used to sit there as a silent black rectangle; now it says so.
    var failed by remember(videoUri) { mutableStateOf(false) }
    // Assumed until the tracks arrive, so the mute button does not flicker into disabled.
    // A sound post always has something to mute, whatever the webm's own tracks say.
    var hasAudio by remember(videoUri) { mutableStateOf(true) }
    val canMute = hasAudio || soundPlayer != null
    var aspect by remember(videoUri) {
        mutableFloatStateOf(
            if (initialWidth > 0 && initialHeight > 0) initialWidth.toFloat() / initialHeight else 16f / 9f,
        )
    }

    LaunchedEffect(selected, playing) { player.playWhenReady = selected && playing }
    LaunchedEffect(muted, soundPlayer) {
        val volume = if (muted) 0f else 1f
        player.volume = volume
        soundPlayer?.volume = volume
    }
    LaunchedEffect(autoAdvance, soundPlayer) {
        val mode = if (autoAdvance) ExoPlayer.REPEAT_MODE_OFF else ExoPlayer.REPEAT_MODE_ONE
        player.repeatMode = mode
        soundPlayer?.repeatMode = mode
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
    // The transport bar is the only reader of the position, so the poll runs only while
    // it is on screen and the position is moving. One read on entry keeps a paused or
    // freshly revealed bar accurate; [isPlaying] itself comes from the player's listener.
    LaunchedEffect(videoUri, isPlaying, chromeVisible) {
        positionMs = player.currentPosition.coerceAtLeast(0)
        durationMs = player.duration.coerceAtLeast(0)
        while (isPlaying && chromeVisible) {
            delay(250)
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
        }
    }
    val autoAdvanceNow = rememberUpdatedState(autoAdvance)
    val onEndedNow = rememberUpdatedState(onEnded)
    val selectedNow = rememberUpdatedState(selected)
    DisposableEffect(videoUri) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED && autoAdvanceNow.value && selectedNow.value) {
                    onEndedNow.value()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
                buffering = false
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracks.audioPresence()?.let { hasAudio = it }
            }
        }
        player.addListener(listener)
        // The player may have prepared before this effect ran.
        player.currentTracks.audioPresence()?.let { hasAudio = it }
        isPlaying = player.isPlaying
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    var viewportWidth by remember { mutableIntStateOf(0) }
    // Signed milliseconds of the last edge jump, shown briefly; 0 while nothing is showing.
    var seekHint by remember { mutableLongStateOf(0L) }
    LaunchedEffect(seekHint) {
        if (seekHint != 0L) {
            delay(700)
            seekHint = 0L
        }
    }

    val zoomState = rememberZoomableState()
    val doubleClick = remember(behaviour, viewportWidth) {
        if (!behaviour.doubleTapSeek || viewportWidth <= 0) {
            DoubleClickToZoomListener.cycle()
        } else {
            EdgeSeekDoubleClick(
                viewportWidth = viewportWidth,
                // Resolved per tap, not per composition: the duration is unknown until
                // the player has prepared, and a short clip must not get the full step.
                onSeek = { direction ->
                    val duration = player.duration
                    val step = behaviour.seekStepMillis(duration) * direction
                    val limit = if (duration > 0) duration else Long.MAX_VALUE
                    player.seekTo((player.currentPosition + step).coerceIn(0L, limit))
                    seekHint = step
                },
                zoom = DoubleClickToZoomListener.cycle(),
            )
        }
    }

    Box(
        Modifier.fillMaxSize().onSizeChanged { viewportWidth = it.width },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            // Pinch-to-zoom over the whole video area; single taps still toggle the chrome.
            Modifier
                .matchParentSize()
                .zoomable(
                    zoomState,
                    onClick = { onToggleChrome() },
                    onLongClick = { onLongPress() },
                    onDoubleClick = doubleClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Bare aspectRatio picks the largest size that satisfies BOTH constraints, so the
            // video letterboxes in landscape instead of filling the width and cropping.
            // TextureView (not the SurfaceView default) is required for zoom transforms to
            // actually render scaled.
            val surfaceAlpha by animateFloatAsState(
                targetValue = if (firstFrameRendered) 1f else 0f,
                animationSpec = tween(LocalMotion.current.short),
                label = "videoSurfaceFadeIn",
            )
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier = Modifier.aspectRatio(aspect).graphicsLayer { alpha = surfaceAlpha },
            )
            // Thumbnail stands in until the first video frame is on screen, so swiping to a
            // video shows a preview instead of a black page while it loads.
            // With a shared key the still stays composed underneath so the closing transition
            // has something to morph back into the thumbnail; the player fades in over it.
            if (!firstFrameRendered || sharedKey != null) {
                AsyncImage(
                    model = thumbnailModel,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .aspectRatio(aspect)
                        .then(if (sharedKey != null) Modifier.sharedMedia(sharedKey) else Modifier)
                        .zIndex(if (firstFrameRendered) -1f else 0f),
                )
            }
        }
        if (seekHint != 0L) {
            Text(
                text = seekLabel(seekHint),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier
                    .align(if (seekHint > 0L) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 48.dp),
            )
        }
        when {
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.media_video_failed),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = {
                        failed = false
                        buffering = true
                        player.prepare()
                        player.playWhenReady = selected && playing
                    },
                ) {
                    Text(stringResource(R.string.action_retry), color = Color.White)
                }
            }
            // Before the first frame the thumbnail is up and the spinner says "loading";
            // after it, the spinner only appears while the stream has actually stalled.
            !firstFrameRendered || buffering -> CircularProgressIndicator(
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
                    .notifyOnPress(onControlTouched)
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
                        onScrubbing(true)
                        if (durationMs > 0) {
                            val target = (f * durationMs).toLong()
                            positionMs = target
                            player.seekTo(target)
                        }
                    },
                    onValueChangeFinished = { onScrubbing(false) },
                    modifier = Modifier.weight(1f).padding(horizontal = spacing.sm),
                )
                Text(
                    formatMs(durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
                // A silent video has nothing to unmute: the button goes dead and says so,
                // rather than leaving a live-looking control that does nothing.
                IconButton(onClick = onToggleMute, enabled = canMute) {
                    Icon(
                        when {
                            !canMute -> Icons.Filled.VolumeMute
                            muted -> Icons.Filled.VolumeOff
                            else -> Icons.Filled.VolumeUp
                        },
                        stringResource(
                            when {
                                !canMute -> R.string.media_no_audio
                                muted -> R.string.media_unmute
                                else -> R.string.media_mute
                            },
                        ),
                        tint = Color.White.copy(alpha = if (canMute) 1f else 0.4f),
                    )
                }
            }
        }
    }
}

/**
 * Whether the file carries audio at all, playable or not — null while the player has not
 * read any tracks yet, which is not the same answer as "no audio" and must not disable
 * the button.
 */
private fun Tracks.audioPresence(): Boolean? =
    if (groups.isEmpty()) null else groups.any { it.type == C.TRACK_TYPE_AUDIO }

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/**
 * Double-tapping the outer [EDGE_FRACTION] of a video seeks; the middle keeps telephoto's
 * zoom. Videos only — an image has nothing to seek, so it keeps zoom everywhere.
 */
private class EdgeSeekDoubleClick(
    private val viewportWidth: Int,
    /** Called with -1 to jump back, +1 to jump forward. */
    private val onSeek: (Int) -> Unit,
    private val zoom: DoubleClickToZoomListener,
) : DoubleClickToZoomListener {
    override suspend fun onDoubleClick(state: ZoomableState, centroid: Offset) {
        val edge = viewportWidth * EDGE_FRACTION
        when {
            centroid.x < edge -> onSeek(-1)
            centroid.x > viewportWidth - edge -> onSeek(1)
            else -> zoom.onDoubleClick(state, centroid)
        }
    }
}

/** "+10 s" for whole seconds, "+0.5 s" once a short clip has scaled the jump down. */
private fun seekLabel(deltaMs: Long): String {
    val seconds = abs(deltaMs) / 1000f
    val amount = if (seconds >= 1f) seconds.roundToInt().toString() else ((seconds * 10).roundToInt() / 10f).toString()
    return (if (deltaMs > 0) "+" else "\u2212") + amount + " s"
}

/** Share of the width at each side that seeks rather than zooms. */
private const val EDGE_FRACTION = 0.3f

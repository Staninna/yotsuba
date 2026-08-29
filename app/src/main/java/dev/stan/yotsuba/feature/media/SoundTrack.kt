package dev.stan.yotsuba.feature.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * The audio half of a sound post: a second player looping [soundUrl] on its own. Null
 * [soundUrl] composes nothing. Used alone by an image page; a video page keeps the
 * returned player and slaves it to the visual with [followVisual].
 */
@Composable
fun rememberSoundPlayer(soundUrl: String?): ExoPlayer? {
    val context = LocalContext.current
    val player = remember(soundUrl) {
        soundUrl?.let {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(ExoMediaItem.fromUri(it))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
            }
        }
    }
    DisposableEffect(player) {
        onDispose { player?.release() }
    }
    return player
}

/** Standalone sound for a still image: plays while asked to, stops with the activity. */
@Composable
fun SoundTrack(soundUrl: String?, playWhenReady: Boolean, muted: Boolean) {
    val player = rememberSoundPlayer(soundUrl) ?: return
    LaunchedEffect(player, playWhenReady) { player.playWhenReady = playWhenReady }
    LaunchedEffect(player, muted) { player.volume = if (muted) 0f else 1f }
    val lifecycleOwner = LocalLifecycleOwner.current
    val shouldPlay = rememberUpdatedState(playWhenReady)
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
}

/**
 * Keeps [sound] in step with [visual]: play/pause together, every seek and every loop
 * restart lands the audio on the same position. Returns the listener so the caller can
 * remove it.
 */
fun ExoPlayer.followVisual(visual: Player): Player.Listener {
    val sound = this
    val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            sound.playWhenReady = playWhenReady
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            sound.seekTo(newPosition.positionMs.coerceAtLeast(0))
        }
    }
    visual.addListener(listener)
    sound.playWhenReady = visual.playWhenReady
    return listener
}

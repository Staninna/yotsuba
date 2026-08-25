package dev.stan.yotsuba.feature.media

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon as AndroidIcon
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import dev.stan.yotsuba.R

private const val PIP_ACTION = "dev.stan.yotsuba.PIP_ACTION"
private const val PIP_EXTRA = "what"
private const val PIP_PREV = 0
private const val PIP_PLAY_PAUSE = 1
private const val PIP_NEXT = 2

/** What the current page shows, for the PiP window's aspect ratio and action row. */
data class PipMediaInfo(val width: Int, val height: Int, val isVideo: Boolean)

/**
 * Owns picture-in-picture for a full-screen viewer: the mode listener, the remote-action
 * broadcast receiver, and params building (aspect clamp + prev/play-pause/next actions).
 */
@Stable
class PipController internal constructor(
    private val context: Context,
    private val activity: ComponentActivity?,
    private val labels: PipLabels,
) {
    var inPipMode by mutableStateOf(false)
        internal set

    fun enter(info: PipMediaInfo?, playing: Boolean) {
        activity?.enterPictureInPictureMode(params(info, playing))
    }

    fun updateParams(info: PipMediaInfo?, playing: Boolean) {
        activity?.setPictureInPictureParams(params(info, playing))
    }

    private fun params(info: PipMediaInfo?, playing: Boolean): PictureInPictureParams {
        val rawAspect = if (info != null && info.width > 0 && info.height > 0) {
            info.width.toFloat() / info.height
        } else 16f / 9f
        val aspect = rawAspect.coerceIn(0.45f, 2.35f)
        val actions = buildList {
            add(action(PIP_PREV, android.R.drawable.ic_media_previous, labels.previous))
            if (info?.isVideo == true) {
                add(
                    action(
                        PIP_PLAY_PAUSE,
                        if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                        if (playing) labels.pause else labels.play,
                    ),
                )
            }
            add(action(PIP_NEXT, android.R.drawable.ic_media_next, labels.next))
        }
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational((aspect * 10_000).toInt(), 10_000))
            .setActions(actions)
            .build()
    }

    private fun action(what: Int, icon: Int, title: String): RemoteAction {
        val pi = PendingIntent.getBroadcast(
            context, what,
            Intent(PIP_ACTION).setPackage(context.packageName).putExtra(PIP_EXTRA, what),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(AndroidIcon.createWithResource(context, icon), title, title, pi)
    }
}

internal data class PipLabels(
    val previous: String,
    val next: String,
    val play: String,
    val pause: String,
)

@Composable
fun rememberPipController(
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTogglePlayPause: () -> Unit,
): PipController {
    val context = LocalContext.current
    val labels = PipLabels(
        previous = stringResource(R.string.media_pip_previous),
        next = stringResource(R.string.media_pip_next),
        play = stringResource(R.string.media_play),
        pause = stringResource(R.string.media_pause),
    )
    val controller = remember(context) {
        PipController(context, context.findComponentActivity(), labels)
    }
    val prev by rememberUpdatedState(onPrev)
    val next by rememberUpdatedState(onNext)
    val togglePlayPause by rememberUpdatedState(onTogglePlayPause)

    DisposableEffect(controller) {
        val activity = context.findComponentActivity()
            ?: return@DisposableEffect onDispose {}
        val listener = Consumer<androidx.core.app.PictureInPictureModeChangedInfo> {
            controller.inPipMode = it.isInPictureInPictureMode
        }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }

    DisposableEffect(controller) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.getIntExtra(PIP_EXTRA, -1)) {
                    PIP_PREV -> prev()
                    PIP_NEXT -> next()
                    PIP_PLAY_PAUSE -> togglePlayPause()
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(PIP_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return controller
}

package dev.stan.yotsuba.feature.media

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.SheetTitle
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.TimeFormat
import dev.stan.yotsuba.core.vault.VideoStills
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long the slider has to rest before a frame is decoded for it; decoding a webm frame is not free. */
private const val SCRUB_DEBOUNCE_MS = 120L

/**
 * Picks one frame out of a local [video]: a preview and a slider over the whole length,
 * starting at [startMs] (the player's position when the menu was opened). "Use this frame"
 * writes the frame as a JPEG into the share cache and hands it to [onPick].
 *
 * One decoder is opened for the sheet's life and every frame comes through it on the IO
 * dispatcher, one at a time; [onFailed] fires when the file will not decode at all.
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun FramePickerSheet(
    video: File,
    startMs: Long,
    onPick: (File) -> Unit,
    onDismiss: () -> Unit,
    onFailed: () -> Unit,
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val scope = rememberCoroutineScope()

    // Opened lazily on IO: setDataSource parses the container, which is a disk read.
    var source by remember(video) { mutableStateOf<VideoStills.FrameSource?>(null) }
    var durationMs by remember(video) { mutableLongStateOf(0L) }
    var positionMs by remember(video) { mutableLongStateOf(startMs) }
    var frame by remember(video) { mutableStateOf<Bitmap?>(null) }
    var decoding by remember(video) { mutableStateOf(true) }
    var saving by remember(video) { mutableStateOf(false) }

    LaunchedEffect(video) {
        val opened = withContext(Dispatchers.IO) { VideoStills.FrameSource.open(video) }
        if (opened == null) {
            onFailed()
            onDismiss()
            return@LaunchedEffect
        }
        source = opened
        durationMs = opened.durationMs
        positionMs = positionMs.coerceIn(0L, opened.durationMs.coerceAtLeast(0L))
        // The first frame comes straight away; after that only once the slider has settled.
        // collectLatest drops a decode the finger has already moved past.
        snapshotFlow { positionMs }
            .debounce { if (frame == null) 0L else SCRUB_DEBOUNCE_MS }
            .collectLatest { ms ->
                decoding = true
                val decoded = withContext(Dispatchers.IO) { opened.frameAt(ms) }
                if (decoded != null) frame = decoded
                decoding = false
            }
    }
    DisposableEffect(video) {
        onDispose { source?.close() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = spacing.xl)) {
            SheetTitle(stringResource(R.string.media_frame_title))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.xl)
                    .aspectRatio(frame?.let { it.width.toFloat() / it.height } ?: (16f / 9f))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                frame?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (decoding) CircularProgressIndicator(color = Color.White)
            }
            val sliderLabel = stringResource(R.string.media_frame_slider)
            Slider(
                value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                onValueChange = { positionMs = (it * durationMs).toLong() },
                enabled = durationMs > 0,
                modifier = Modifier
                    .padding(horizontal = spacing.xl)
                    .semantics { contentDescription = sliderLabel },
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${TimeFormat.duration(positionMs)} / ${TimeFormat.duration(durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = frame != null && !decoding && !saving,
                    onClick = {
                        val bitmap = frame ?: return@Button
                        scope.launch {
                            saving = true
                            val file = withContext(Dispatchers.IO) {
                                ShareCache.writeJpeg(context, bitmap, ShareCache.frameFileName(video, positionMs))
                            }
                            saving = false
                            if (file != null) onPick(file) else onFailed()
                        }
                    },
                ) {
                    Text(stringResource(R.string.media_frame_use))
                }
            }
        }
    }
}

/** "Fetching the video…" with a cancel button, while a remote video is pulled down for the picker. */
@Composable
fun FetchingVideoDialog(onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.media_frame_fetching),
                    modifier = Modifier.padding(start = LocalSpacing.current.md),
                )
            }
        },
    )
}

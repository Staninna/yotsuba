package dev.stan.yotsuba.feature.media

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.SheetActionRow
import dev.stan.yotsuba.core.designsystem.component.SheetTitle
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing

/**
 * Where to send the picture: one row per engine, then the share sheet. Media with an
 * online copy opens by URL straight away. A file that only exists on this phone (a video
 * frame, an imported file) goes to Lens as a shared image and to every other engine by
 * upload: the engine's own form or a temporary host, per the privacy setting. The host
 * route always asks first: the sheet shows a confirm row and nothing leaves the phone until
 * it is tapped. The sheet stays up while an upload runs, and a failed direct upload offers
 * the host as a retry.
 *
 * [onFailed] fires when nothing on the device could take the request.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReverseSearchSheet(
    target: ReverseSearchTarget,
    onDismiss: () -> Unit,
    onFailed: () -> Unit,
    viewModel: ReverseSearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uploading = state as? LocalSearchState.Uploading
    val failed = state as? LocalSearchState.Failed
    val confirm = state as? LocalSearchState.ConfirmHost

    // The upload's result: open the page it landed on and take the sheet down.
    LaunchedEffect(state) {
        val opened = state as? LocalSearchState.Opened ?: return@LaunchedEffect
        if (!openInBrowser(context, opened.url)) onFailed()
        viewModel.reset()
        onDismiss()
    }

    val dismiss = {
        viewModel.reset()
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.padding(bottom = spacing.xl)) {
            SheetTitle(stringResource(R.string.media_search_title))
            if (!target.canUseEngines) {
                Text(
                    stringResource(R.string.media_search_uploads_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.xl).padding(bottom = spacing.sm),
                )
            }
            ReverseSearchEngine.entries.forEach { engine ->
                SheetActionRow(
                    label = engine.label,
                    icon = Icons.Filled.Search,
                    enabled = target.canUse(engine) && uploading == null,
                    supporting = when {
                        uploading?.engine == engine -> stringResource(R.string.media_search_uploading)
                        confirm?.engine == engine ->
                            stringResource(R.string.media_search_confirm_host_note, engine.label)
                        failed?.engine == engine -> stringResource(R.string.media_search_upload_failed, engine.label)
                        else -> null
                    },
                    trailing = if (uploading?.engine == engine) {
                        { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                    } else {
                        null
                    },
                    onClick = {
                        val url = target.remoteUrl
                        val file = target.file
                        when {
                            url != null -> {
                                dismiss()
                                if (!openInBrowser(context, engine.searchUrl(url))) onFailed()
                            }
                            file != null && engine.takesSharedImage -> {
                                dismiss()
                                if (!searchFileWithLens(context, file, target.ext)) onFailed()
                            }
                            file != null -> viewModel.search(engine, file, target.ext)
                        }
                    },
                )
            }
            // The host only ever gets the file from one of these two explicit taps.
            val hostEngine = confirm?.engine ?: failed?.takeIf { it.canFallback }?.engine
            if (hostEngine != null && target.file != null) {
                SheetActionRow(
                    label = stringResource(
                        if (confirm != null) R.string.media_search_confirm_host else R.string.media_search_retry_host,
                    ),
                    icon = Icons.Filled.CloudUpload,
                    onClick = {
                        target.file?.let { viewModel.search(hostEngine, it, target.ext, hostConfirmed = true) }
                    },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = spacing.sm))
            SheetActionRow(
                label = stringResource(R.string.media_search_share_app),
                icon = Icons.Filled.Share,
                enabled = target.canShare,
                onClick = {
                    dismiss()
                    val file = target.file ?: return@SheetActionRow
                    if (!shareMediaFile(context, file, target.ext)) onFailed()
                },
            )
        }
    }
}

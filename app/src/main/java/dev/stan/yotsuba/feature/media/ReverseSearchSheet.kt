package dev.stan.yotsuba.feature.media

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.SheetActionRow
import dev.stan.yotsuba.core.designsystem.component.SheetTitle
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing

/**
 * Where to send the picture: one row per engine, then the share sheet. The engines need
 * a URL they can fetch, so for a file that only exists on this phone (a video frame, an
 * imported file) all but Lens sit greyed out, with the reason said once under the title.
 * Lens takes the file itself as a shared image, and the share row is the way to anything
 * else.
 *
 * [onFailed] fires when nothing on the device could take the request.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReverseSearchSheet(
    target: ReverseSearchTarget,
    onDismiss: () -> Unit,
    onFailed: () -> Unit,
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val needsUrl = stringResource(R.string.media_search_needs_url)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = spacing.xl)) {
            SheetTitle(stringResource(R.string.media_search_title))
            if (!target.canUseEngines) {
                Text(
                    needsUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.xl).padding(bottom = spacing.sm),
                )
            }
            ReverseSearchEngine.entries.forEach { engine ->
                SheetActionRow(
                    label = engine.label,
                    icon = Icons.Filled.Search,
                    enabled = target.canUse(engine),
                    onClick = {
                        onDismiss()
                        val url = target.remoteUrl
                        val file = target.file
                        val opened = when {
                            url != null -> openInBrowser(context, engine.searchUrl(url))
                            file != null && engine.takesSharedImage -> searchFileWithLens(context, file, target.ext)
                            else -> return@SheetActionRow
                        }
                        if (!opened) onFailed()
                    },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = spacing.sm))
            SheetActionRow(
                label = stringResource(R.string.media_search_share_app),
                icon = Icons.Filled.Share,
                enabled = target.canShare,
                onClick = {
                    onDismiss()
                    val file = target.file ?: return@SheetActionRow
                    if (!shareMediaFile(context, file, target.ext)) onFailed()
                },
            )
        }
    }
}

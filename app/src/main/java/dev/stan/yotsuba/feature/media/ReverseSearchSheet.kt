package dev.stan.yotsuba.feature.media

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
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
 * a URL they can fetch, so for a file that only exists on this phone they sit greyed out
 * with the reason under the first one, and sharing is the way through: that is how Lens
 * and its kind take a local image.
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
            ReverseSearchEngine.entries.forEachIndexed { i, engine ->
                SheetActionRow(
                    label = engine.label,
                    icon = Icons.Filled.Search,
                    enabled = target.canUseEngines,
                    supporting = needsUrl.takeIf { !target.canUseEngines && i == 0 },
                    onClick = {
                        onDismiss()
                        val url = target.remoteUrl ?: return@SheetActionRow
                        if (!openInBrowser(context, engine.searchUrl(url))) onFailed()
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

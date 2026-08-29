package dev.stan.yotsuba.feature.thread.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.domain.model.ThreadPost

/** Long-press menu for one post. Each callback closes the sheet itself. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostActionSheet(
    post: ThreadPost,
    claimed: Boolean,
    showFilterById: Boolean,
    onCopyText: () -> Unit,
    onShareLink: () -> Unit,
    onCopyImageUrl: () -> Unit,
    onToggleClaimed: () -> Unit,
    onFilterById: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column {
            Text(
                "#${post.no}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = spacing.lg),
            )
            SheetRow(Icons.Filled.ContentCopy, stringResource(R.string.thread_copy_text), onCopyText)
            SheetRow(Icons.Filled.Share, stringResource(R.string.thread_share_post_link), onShareLink)
            if (post.presentMedia != null) {
                SheetRow(Icons.Filled.Image, stringResource(R.string.thread_copy_image_url), onCopyImageUrl)
            }
            SheetRow(
                Icons.Filled.Person,
                stringResource(if (claimed) R.string.thread_unmark_as_mine else R.string.thread_mark_as_mine),
                onToggleClaimed,
            )
            if (showFilterById && post.posterId != null) {
                SheetRow(Icons.Filled.FilterList, stringResource(R.string.thread_filter_by_id), onFilterById)
            }
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

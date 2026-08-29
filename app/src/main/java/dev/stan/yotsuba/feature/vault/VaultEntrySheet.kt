package dev.stan.yotsuba.feature.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.stan.yotsuba.R
import dev.stan.yotsuba.domain.model.VaultEntry

/**
 * The grid's long-press sheet: what the file is, and everything that can be done with it.
 * Thread and post links only exist for files that came from a live thread; imported and
 * unsorted files have nowhere to go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultEntrySheet(
    entry: VaultEntry,
    onDismiss: () -> Unit,
    onSelect: () -> Unit,
    onOpenThread: (postNo: Long?) -> Unit,
    onShare: () -> Unit,
    onSaveToGallery: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                entry.displayName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            entryDetails(entry).takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SheetAction(R.string.vault_select, Icons.Filled.CheckCircle, onSelect)
            if (entry.location.isRemote) {
                SheetAction(R.string.vault_open_thread, Icons.AutoMirrored.Filled.OpenInNew) { onOpenThread(null) }
                if (entry.postNo != null) {
                    SheetAction(R.string.vault_go_to_post, Icons.Filled.Tag) { onOpenThread(entry.postNo) }
                }
            }
            SheetAction(R.string.thread_share, Icons.Filled.Share, onShare)
            SheetAction(R.string.vault_save_to_gallery, Icons.Filled.SaveAlt, onSaveToGallery)
            SheetAction(R.string.vault_delete, Icons.Filled.Delete, onDelete)
        }
    }
}

@Composable
private fun SheetAction(labelRes: Int, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(labelRes)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Dimensions and post number; size and duration join in later. */
internal fun entryDetails(entry: VaultEntry): String = buildList {
    if ((entry.width ?: 0) > 0) add("${entry.width}×${entry.height}")
    entry.postNo?.let { add("No. $it") }
}.joinToString(" · ")

package dev.stan.yotsuba.feature.vault

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.SheetActionRow
import dev.stan.yotsuba.core.designsystem.component.SheetTitle
import dev.stan.yotsuba.core.util.FileSize
import dev.stan.yotsuba.core.util.TimeFormat
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
            SheetTitle(entry.displayName)
            entryDetails(entry).takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SheetActionRow(stringResource(R.string.vault_select), Icons.Filled.CheckCircle, onSelect)
            if (entry.location.isRemote) {
                SheetActionRow(
                    stringResource(R.string.vault_open_thread),
                    Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = { onOpenThread(null) },
                )
                if (entry.postNo != null) {
                    SheetActionRow(stringResource(R.string.vault_go_to_post), Icons.Filled.Tag, onClick = { onOpenThread(entry.postNo) })
                }
            }
            SheetActionRow(stringResource(R.string.thread_share), Icons.Filled.Share, onShare)
            SheetActionRow(stringResource(R.string.vault_save_to_gallery), Icons.Filled.SaveAlt, onSaveToGallery)
            SheetActionRow(stringResource(R.string.vault_delete), Icons.Filled.Delete, onDelete)
        }
    }
}

/** Size, dimensions, length, post number and save date: what the tile cannot fit. */
internal fun entryDetails(entry: VaultEntry): String = buildList {
    entry.sizeBytes?.let { add(FileSize.format(it)) }
    if ((entry.width ?: 0) > 0) add("${entry.width}×${entry.height}")
    entry.durationMs?.let { add(TimeFormat.duration(it)) }
    entry.postNo?.let { add("No. $it") }
    add(TimeFormat.date(entry.savedAt))
}.joinToString(" · ")

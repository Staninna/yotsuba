package dev.stan.yotsuba.feature.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.stan.yotsuba.R
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.VaultError

/**
 * The viewer's save button: status icon plus the manage menu (remove/redownload/cancel/
 * retry/dismiss) for anything with an existing state. A plain tap with no [status]
 * saves; [interceptClick] runs first and consumes the tap when it returns true (storage gate).
 */
@Composable
internal fun DownloadAction(
    status: MediaSaveStatus?,
    interceptClick: () -> Boolean,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onRedownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismissFailed: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = {
            when {
                interceptClick() -> Unit
                status == null -> onSave()
                else -> menuOpen = true
            }
        }) {
            SaveStatusIcon(status)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            val close = { menuOpen = false }
            when (status) {
                MediaSaveStatus.Saved -> {
                    MenuItem(R.string.media_remove_download) { close(); onRemove() }
                    MenuItem(R.string.media_redownload) { close(); onRedownload() }
                }
                MediaSaveStatus.Queued ->
                    MenuItem(R.string.media_cancel_download) { close(); onCancel() }
                MediaSaveStatus.Downloading ->
                    MenuItem(R.string.media_downloading, enabled = false) {}
                is MediaSaveStatus.Failed -> {
                    MenuItem(errorLabel(status.error), enabled = false) {}
                    MenuItem(R.string.media_retry_download) { close(); onRetry() }
                    MenuItem(R.string.media_dismiss_failed) { close(); onDismissFailed() }
                }
                is MediaSaveStatus.AlreadySaved -> {
                    MenuItem(R.string.media_already_saved, enabled = false) {}
                    MenuItem(R.string.media_dismiss_failed) { close(); onDismissFailed() }
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun MenuItem(labelRes: Int, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun SaveStatusIcon(status: MediaSaveStatus?) {
    when (status) {
        MediaSaveStatus.Saved -> Icon(
            Icons.Filled.DownloadDone,
            stringResource(R.string.media_downloaded),
            tint = Color(0xFF81C784),
        )
        MediaSaveStatus.Downloading -> CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = Color.White,
            strokeWidth = 2.dp,
        )
        MediaSaveStatus.Queued -> Icon(
            Icons.Filled.Schedule,
            stringResource(R.string.media_queued),
            tint = Color.White.copy(alpha = 0.7f),
        )
        is MediaSaveStatus.Failed -> Icon(
            Icons.Filled.Download,
            stringResource(R.string.media_save_failed),
            tint = Color(0xFFE57373),
        )
        is MediaSaveStatus.AlreadySaved -> Icon(
            Icons.Filled.DownloadDone,
            stringResource(R.string.media_already_saved),
            tint = Color(0xFF81C784).copy(alpha = 0.7f),
        )
        null -> Icon(
            Icons.Filled.Download,
            stringResource(R.string.media_save),
            tint = Color.White,
        )
    }
}

private fun errorLabel(error: VaultError): Int = when (error) {
    is VaultError.NoAccess -> R.string.vault_error_no_access
    is VaultError.NotFound -> R.string.vault_error_not_found
    is VaultError.Io -> R.string.vault_error_io
}

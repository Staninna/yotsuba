package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.SectionHeader
import dev.stan.yotsuba.core.designsystem.component.SwitchRow
import dev.stan.yotsuba.core.designsystem.component.TextRow
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.repository.BackupInfo
import dev.stan.yotsuba.domain.repository.BackupResult
import java.text.DateFormat
import java.util.Date

@Composable
fun StorageSection(
    settings: Settings,
    update: ((Settings) -> Settings) -> Unit,
    onClearCache: () -> Unit,
    onClearHistory: () -> Unit,
    onClearBookmarks: () -> Unit,
    confirmThen: (Int, () -> Unit) -> Unit,
    showMessage: (String) -> Unit,
    restoreAvailable: BackupInfo? = null,
    backupResult: BackupResult? = null,
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onDismissRestore: () -> Unit = {},
    onBackupResultShown: () -> Unit = {},
) {
    val cleared = stringResource(R.string.settings_cleared)

    fun clear(bodyRes: Int, action: () -> Unit) = confirmThen(bodyRes) {
        action()
        showMessage(cleared)
    }

    restoreAvailable?.let { info ->
        RestoreCard(info, onRestore = onImportBackup, onDismiss = onDismissRestore)
    }

    TextRow(stringResource(R.string.settings_clear_cache)) {
        clear(R.string.settings_confirm_clear_cache_body, onClearCache)
    }
    TextRow(stringResource(R.string.settings_clear_history)) {
        clear(R.string.settings_confirm_clear_history_body, onClearHistory)
    }
    TextRow(stringResource(R.string.settings_clear_bookmarks)) {
        clear(R.string.settings_confirm_clear_bookmarks_body, onClearBookmarks)
    }
    TextRow(stringResource(R.string.settings_clear_trusted)) {
        clear(R.string.settings_confirm_clear_trusted_body) { update { it.copy(trustedDomains = emptySet()) } }
    }
    SwitchRow(
        title = stringResource(R.string.settings_confirm_vault_delete),
        summary = stringResource(R.string.settings_confirm_vault_delete_summary),
        checked = settings.confirmVaultDelete,
        onToggle = { v -> update { it.copy(confirmVaultDelete = v) } },
    )
    SwitchRow(
        title = stringResource(R.string.vault_sync_snapshot_watched),
        summary = stringResource(R.string.vault_sync_snapshot_watched_summary),
        checked = settings.snapshotWatchedThreads,
        onToggle = { v -> update { it.copy(snapshotWatchedThreads = v) } },
    )
    SwitchRow(
        title = stringResource(R.string.vault_sync_prune_dead),
        summary = stringResource(
            if (settings.pruneDeadSidecars) R.string.vault_sync_prune_dead_summary_on
            else R.string.vault_sync_prune_dead_summary_off,
        ),
        checked = settings.pruneDeadSidecars,
        onToggle = { v -> update { it.copy(pruneDeadSidecars = v) } },
    )

    SectionHeader(stringResource(R.string.backup_header))
    TextRow(
        title = stringResource(R.string.backup_export_now),
        summary = stringResource(R.string.backup_export_summary),
        onClick = onExportBackup,
    )
    TextRow(
        title = stringResource(R.string.backup_import),
        summary = stringResource(R.string.backup_import_summary),
    ) {
        confirmThen(R.string.backup_confirm_import_body, onImportBackup)
    }

    val message = backupResult?.let { resultMessage(it) }
    LaunchedEffect(backupResult) {
        if (message != null) {
            showMessage(message)
            onBackupResultShown()
        }
    }
}

@Composable
private fun RestoreCard(info: BackupInfo, onRestore: () -> Unit, onDismiss: () -> Unit) {
    val spacing = LocalSpacing.current
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
    ) {
        Column(Modifier.padding(spacing.lg)) {
            Text(
                stringResource(R.string.backup_found_title, formatDate(info.exportedAt)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.backup_found_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_dismiss)) }
                TextButton(onClick = onRestore) { Text(stringResource(R.string.backup_restore)) }
            }
        }
    }
}

@Composable
private fun resultMessage(result: BackupResult): String = when (result) {
    is BackupResult.Exported -> stringResource(R.string.backup_exported, formatDate(result.exportedAt))
    is BackupResult.Imported -> stringResource(R.string.backup_imported, result.bookmarks, result.hiddenThreads)
    BackupResult.NoAccess -> stringResource(R.string.backup_no_access)
    BackupResult.NoBackup -> stringResource(R.string.backup_none_found)
    is BackupResult.Failed -> stringResource(R.string.backup_failed, result.message)
}

private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

package dev.stan.yotsuba.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.backup.BackupManager
import dev.stan.yotsuba.core.designsystem.component.SectionHeader
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing

/**
 * Export/import for everything that lives inside the app sandbox. The media
 * vault isn't here because it already survives on its own.
 */
@Composable
fun BackupSection(
    result: BackupManager.Result?,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val spacing = LocalSpacing.current

    SectionHeader(stringResource(R.string.settings_backup))
    Column(Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs)) {
        Text(
            stringResource(R.string.settings_backup_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(top = spacing.sm)) {
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = spacing.sm),
            ) { Text(stringResource(R.string.settings_backup_export)) }
            OutlinedButton(
                onClick = onImport,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.settings_backup_import)) }
        }
        when (result) {
            is BackupManager.Result.Exported -> Message(
                stringResource(
                    R.string.settings_backup_exported,
                    result.path,
                    result.counts.bookmarks,
                    result.counts.history,
                    result.counts.hiddenThreads,
                ),
            )
            is BackupManager.Result.Imported -> Message(
                stringResource(
                    R.string.settings_backup_imported,
                    result.counts.bookmarks,
                    result.counts.history,
                    result.counts.hiddenThreads,
                ),
            )
            is BackupManager.Result.Failed -> Message(result.message, error = true)
            null -> Unit
        }
    }
}

@Composable
private fun Message(text: String, error: Boolean = false) {
    val spacing = LocalSpacing.current
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.sm),
    )
}

package dev.stan.yotsuba.feature.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.FileSize
import dev.stan.yotsuba.domain.model.VaultEntry

/**
 * What a grid delete moved aside, newest first, each with a Restore button. The undo
 * snackbar covers the first half minute; this is the way back after it, for a week.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultTrashSheet(
    entries: List<VaultEntry>,
    onDismiss: () -> Unit,
    onRestore: (String) -> Unit,
    onEmpty: () -> Unit,
) {
    val spacing = LocalSpacing.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = spacing.lg)) {
            item {
                Text(stringResource(R.string.vault_trash_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.vault_trash_kept),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.md))
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.vault_trash_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = spacing.xl),
                    )
                }
                return@LazyColumn
            }
            items(entries, key = { it.url }) { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val detail = listOfNotNull(
                            entry.subject ?: "/${entry.location.board}/",
                            entry.sizeBytes?.let { FileSize.format(it) },
                        ).joinToString("  ")
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = { onRestore(entry.url) }) { Text(stringResource(R.string.vault_trash_restore)) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = spacing.md), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEmpty) { Text(stringResource(R.string.vault_trash_empty_action)) }
                }
            }
        }
    }
}

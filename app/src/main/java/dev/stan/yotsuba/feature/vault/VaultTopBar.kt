package dev.stan.yotsuba.feature.vault

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing

/** What the browse bar's buttons and menu entries do. */
internal class VaultBarActions(
    val onNavigateUp: () -> Unit,
    val onSearch: () -> Unit,
    val onImportFiles: () -> Unit,
    val onImportFolder: () -> Unit,
    val onRescan: () -> Unit,
    val onFetchReplies: () -> Unit,
    val onStats: () -> Unit,
    val onDedup: () -> Unit,
    val onOpenSettings: () -> Unit,
)

/**
 * The default top bar: title and count for the level on screen, the up arrow inside a
 * drill-down, then search, import, sync (or its progress), the overflow and settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultBrowseTopBar(state: VaultUiState, actions: VaultBarActions) {
    TopAppBar(
        title = {
            Column {
                Text(vaultTitle(state), maxLines = 1)
                if (state.hasStorageAccess && state.entries.isNotEmpty()) {
                    Text(
                        itemsSummary(state.scopeEntries.size, state.scopeEntries.totalBytes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        },
        navigationIcon = {
            if (state.selection.board != null) {
                IconButton(onClick = actions.onNavigateUp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.media_back))
                }
            }
        },
        actions = {
            if (state.hasStorageAccess && state.entries.isNotEmpty()) {
                IconButton(onClick = actions.onSearch) {
                    Icon(Icons.Filled.Search, stringResource(R.string.vault_search))
                }
            }
            if (state.hasStorageAccess) {
                ImportMenu(enabled = !state.importing, actions.onImportFiles, actions.onImportFolder)
            }
            if (state.sync.running) {
                SyncProgress(state.sync)
            } else if (state.hasStorageAccess) {
                SyncMenu(actions.onRescan, actions.onFetchReplies)
            }
            if (state.hasStorageAccess) {
                MoreMenu(actions.onStats, actions.onDedup)
            }
            IconButton(onClick = actions.onOpenSettings) {
                Icon(Icons.Outlined.Settings, stringResource(R.string.action_open_settings))
            }
        },
    )
}

@Composable
private fun vaultTitle(state: VaultUiState): String = when {
    state.selection.board == null -> stringResource(R.string.vault_title)
    state.selection.thread == null -> boardTitle(state.selection.board!!)
    else -> threadTitle(state.selection.thread!!, state.openThread?.subject)
}

/** An icon button whose menu closes itself before each entry's action runs. */
@Composable
private fun BarMenu(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    entries: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(enabled = enabled, onClick = { open = true }) {
            Icon(icon, contentDescription)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries { open = false }
        }
    }
}

@Composable
private fun ImportMenu(enabled: Boolean, onFiles: () -> Unit, onFolder: () -> Unit) {
    BarMenu(Icons.Filled.Add, stringResource(R.string.vault_import), enabled) { close ->
        DropdownMenuItem(
            text = { Text(stringResource(R.string.vault_import_files)) },
            onClick = { close(); onFiles() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.vault_import_folder)) },
            onClick = { close(); onFolder() },
        )
    }
}

@Composable
private fun SyncMenu(onRescan: () -> Unit, onFetchReplies: () -> Unit) {
    BarMenu(Icons.Filled.Refresh, stringResource(R.string.vault_sync)) { close ->
        DropdownMenuItem(
            text = { MenuLabel(R.string.vault_rescan_label, R.string.vault_rescan_explanation) },
            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            onClick = { close(); onRescan() },
        )
        DropdownMenuItem(
            text = { MenuLabel(R.string.vault_fetch_replies, R.string.vault_fetch_replies_explanation) },
            leadingIcon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
            onClick = { close(); onFetchReplies() },
        )
    }
}

/** Tools that are not a refresh: they used to hide behind the sync icon. */
@Composable
private fun MoreMenu(onStats: () -> Unit, onDedup: () -> Unit) {
    BarMenu(Icons.Filled.MoreVert, stringResource(R.string.vault_more)) { close ->
        DropdownMenuItem(
            text = { MenuLabel(R.string.vault_stats_label, R.string.vault_stats_explanation) },
            leadingIcon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
            onClick = { close(); onStats() },
        )
        DropdownMenuItem(
            text = { MenuLabel(R.string.vault_dedup_label, R.string.vault_dedup_explanation) },
            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
            onClick = { close(); onDedup() },
        )
    }
}

/**
 * The counter matters: a rate-limited sync of many threads takes about a second each,
 * and a bare spinner looks hung.
 */
@Composable
private fun SyncProgress(sync: VaultSyncState) {
    val spacing = LocalSpacing.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (sync.total > 0) {
            Text(
                stringResource(R.string.vault_sync_progress, sync.done, sync.total),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.width(spacing.sm))
        }
        CircularProgressIndicator(Modifier.size(spacing.xl), strokeWidth = 2.dp)
        Spacer(Modifier.width(spacing.md))
    }
}

/** A menu entry that says what it does underneath its name. */
@Composable
private fun MenuLabel(titleRes: Int, explanationRes: Int) {
    Column {
        Text(stringResource(titleRes))
        Text(
            stringResource(explanationRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The top bar as a search field; the query lives in the VM so rotation keeps it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchTopBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text(stringResource(R.string.vault_search_hint)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.media_back))
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.vault_search_clear))
                }
            }
        },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Replaces the top bar while items are ticked: the count, and what can be done with them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionTopBar(
    count: Int,
    onClear: () -> Unit,
    onShare: () -> Unit,
    onSaveToGallery: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.vault_selected_count, count, count)) },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, stringResource(R.string.vault_clear_selection))
            }
        },
        actions = {
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, stringResource(R.string.thread_share)) }
            IconButton(onClick = onSaveToGallery) {
                Icon(Icons.Filled.SaveAlt, stringResource(R.string.vault_save_to_gallery))
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.vault_delete)) }
        },
    )
}

package dev.stan.yotsuba.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.HistoryRetention
import dev.stan.yotsuba.domain.model.MediaAutoplay
import dev.stan.yotsuba.domain.model.ThemeMode
import dev.stan.yotsuba.core.designsystem.component.SectionHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val backupResult by viewModel.backupResult.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val spacing = LocalSpacing.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clearedMessage = stringResource(R.string.settings_cleared)
    var confirmAction by remember { mutableStateOf<Pair<Int, () -> Unit>?>(null) }
    var showTrusted by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    val s = state.settings

    fun confirmThen(bodyRes: Int, action: () -> Unit) { confirmAction = bodyRes to action }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.settings_appearance))
            ChipRow(
                label = stringResource(R.string.settings_theme),
                options = ThemeMode.entries,
                labelOf = { it.labelRes },
                selected = s.themeMode,
                onSelect = { mode -> viewModel.update { it.copy(themeMode = mode) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                summary = stringResource(R.string.settings_dynamic_color_summary),
                checked = s.dynamicColor,
                onToggle = { v -> viewModel.update { it.copy(dynamicColor = v) } },
            )
            ChipRow(
                label = stringResource(R.string.settings_catalog_layout),
                options = CatalogLayout.entries,
                labelOf = { it.labelRes },
                selected = s.catalogLayout,
                onSelect = { layout -> viewModel.update { it.copy(catalogLayout = layout) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_reveal_spoilers),
                summary = stringResource(R.string.settings_reveal_spoilers_summary),
                checked = s.revealAllSpoilers,
                onToggle = { v -> viewModel.update { it.copy(revealAllSpoilers = v) } },
            )

            SectionHeader(stringResource(R.string.settings_behavior))
            SwitchRow(
                title = stringResource(R.string.settings_auto_refresh),
                summary = stringResource(R.string.settings_auto_refresh_summary),
                checked = s.autoRefreshEnabled,
                onToggle = { v -> viewModel.update { it.copy(autoRefreshEnabled = v) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_confirm_links),
                summary = stringResource(R.string.settings_confirm_links_summary),
                checked = s.confirmBeforeOpeningLinks,
                onToggle = { v -> viewModel.update { it.copy(confirmBeforeOpeningLinks = v) } },
            )
            TextRow(
                title = stringResource(R.string.settings_trusted_domains, s.trustedDomains.size),
                onClick = { showTrusted = true },
            )
            ChipRow(
                label = stringResource(R.string.settings_media_autoplay),
                options = MediaAutoplay.entries,
                labelOf = { it.labelRes },
                selected = s.mediaAutoplay,
                onSelect = { v -> viewModel.update { it.copy(mediaAutoplay = v) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_record_history),
                summary = null,
                checked = s.recordHistory,
                onToggle = { v -> viewModel.update { it.copy(recordHistory = v) } },
            )
            ChipRow(
                label = stringResource(R.string.settings_history_retention),
                options = HistoryRetention.entries,
                labelOf = { it.labelRes },
                selected = s.historyRetention,
                onSelect = { v -> viewModel.update { it.copy(historyRetention = v) } },
            )
            TextRow(
                title = stringResource(R.string.settings_hide_nsfw),
                summary = stringResource(R.string.settings_hide_nsfw_summary),
                onClick = { confirmThen(R.string.settings_confirm_hide_nsfw_body) { viewModel.onHideNsfwBoards() } },
            )

            SectionHeader(stringResource(R.string.settings_data))
            TextRow(stringResource(R.string.settings_clear_cache)) {
                confirmThen(R.string.settings_confirm_clear_cache_body) {
                    viewModel.onClearCache()
                    scope.launch { snackbar.showSnackbar(clearedMessage) }
                }
            }
            TextRow(stringResource(R.string.settings_clear_history)) {
                confirmThen(R.string.settings_confirm_clear_history_body) {
                    viewModel.onClearHistory()
                    scope.launch { snackbar.showSnackbar(clearedMessage) }
                }
            }
            TextRow(stringResource(R.string.settings_clear_bookmarks)) {
                confirmThen(R.string.settings_confirm_clear_bookmarks_body) {
                    viewModel.onClearBookmarks()
                    scope.launch { snackbar.showSnackbar(clearedMessage) }
                }
            }
            TextRow(stringResource(R.string.settings_clear_trusted)) {
                confirmThen(R.string.settings_confirm_clear_trusted_body) {
                    viewModel.onClearTrustedDomains()
                    scope.launch { snackbar.showSnackbar(clearedMessage) }
                }
            }
            TextRow(stringResource(R.string.settings_hidden_threads, state.hiddenThreads.size)) {
                showHidden = true
            }

            BackupSection(
                result = backupResult,
                onExport = viewModel::onExportBackup,
                onImport = viewModel::onImportBackup,
            )

            UpdatesSection(
                state = updateState,
                settings = s,
                version = state.versionName,
                onCheck = viewModel::onCheckForUpdates,
                onInstall = viewModel::onInstallUpdate,
                onTokenChange = { value -> viewModel.update { it.copy(updateToken = value) } },
                canInstallPackages = viewModel::canInstallPackages,
                onRequestInstallPermission = { context.startActivity(viewModel.unknownSourcesIntent()) },
            )

            SectionHeader(stringResource(R.string.settings_about))
            Text(
                stringResource(R.string.settings_version, state.versionName),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
            )
            Text(
                stringResource(R.string.settings_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
            )
        }
    }

    confirmAction?.let { (bodyRes, action) ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(stringResource(R.string.settings_confirm_title)) },
            text = { Text(stringResource(bodyRes)) },
            confirmButton = {
                TextButton(onClick = { action(); confirmAction = null }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showTrusted) {
        ManagedListDialog(
            title = stringResource(R.string.settings_trusted_domains, s.trustedDomains.size),
            items = s.trustedDomains.sorted(),
            key = { it },
            itemLabel = { it },
            removeLabel = stringResource(R.string.action_remove),
            onRemove = viewModel::onRevokeTrustedDomain,
            onDismiss = { showTrusted = false },
        )
    }

    if (showHidden) {
        ManagedListDialog(
            title = stringResource(R.string.settings_hidden_threads, state.hiddenThreads.size),
            items = state.hiddenThreads,
            key = { it.board + "/" + it.threadNo },
            itemLabel = { "/${it.board}/${it.threadNo}" },
            removeLabel = stringResource(R.string.settings_unhide),
            onRemove = viewModel::onUnhideThread,
            onDismiss = { showHidden = false },
        )
    }
}

@Composable
private fun <T> ManagedListDialog(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    itemLabel: (T) -> String,
    removeLabel: String,
    onRemove: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (items.isEmpty()) {
                Text(stringResource(R.string.settings_list_empty))
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(items.size, key = { key(items[it]) }) { i ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(itemLabel(items[i]), Modifier.weight(1f))
                            TextButton(onClick = { onRemove(items[i]) }) {
                                Text(removeLabel)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

@Composable
private fun SwitchRow(title: String, summary: String?, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            summary?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun TextRow(title: String, summary: String? = null, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        summary?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun <T> ChipRow(
    label: String,
    options: List<T>,
    labelOf: (T) -> Int,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        androidx.compose.foundation.layout.FlowRow {
            options.forEach { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(stringResource(labelOf(value))) },
                    modifier = Modifier.padding(end = spacing.sm),
                )
            }
        }
    }
}

private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

private val CatalogLayout.labelRes: Int
    get() = when (this) {
        CatalogLayout.COMFORTABLE -> R.string.settings_layout_comfortable
        CatalogLayout.COMPACT -> R.string.settings_layout_compact
        CatalogLayout.LIST -> R.string.settings_layout_list
    }

private val MediaAutoplay.labelRes: Int
    get() = when (this) {
        MediaAutoplay.ALWAYS -> R.string.settings_autoplay_always
        MediaAutoplay.UNMETERED_ONLY -> R.string.settings_autoplay_unmetered
        MediaAutoplay.NEVER -> R.string.settings_autoplay_never
    }

private val HistoryRetention.labelRes: Int
    get() = when (this) {
        HistoryRetention.FOREVER -> R.string.settings_retention_forever
        HistoryRetention.DAYS_30 -> R.string.settings_retention_30d
        HistoryRetention.DAYS_7 -> R.string.settings_retention_7d
    }

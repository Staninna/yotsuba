package dev.stan.yotsuba.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.EnumSegmentedRow
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.FileSize
import dev.stan.yotsuba.core.util.TimeFormat
import dev.stan.yotsuba.domain.model.DedupMode
import dev.stan.yotsuba.domain.model.DuplicateEntry
import dev.stan.yotsuba.domain.model.DuplicateGroup
import java.io.File

/**
 * Duplicate review: hashes whatever still needs it, then lists groups of identical or
 * similar files with a suggested keeper ticked. Deletes go through the vault one at a
 * time and the list refreshes when they are done. [onNotice] gets a one-line summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultDedupSheet(
    onDismiss: () -> Unit,
    onNotice: (String) -> Unit,
    viewModel: VaultDedupViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Every delete here is permanent, so both the per-group button and "apply all" go
    // through the same confirmation before anything is touched.
    var pending by remember { mutableStateOf<PendingDelete?>(null) }
    LaunchedEffect(Unit) { viewModel.start() }

    state.lastDeleted?.let { deleted ->
        val message = buildString {
            append(pluralStringResource(R.plurals.vault_dedup_deleted, deleted, deleted))
            if (state.lastFailed > 0) append(" · ").append(stringResource(R.string.vault_dedup_delete_failed, state.lastFailed))
        }
        LaunchedEffect(deleted, state.lastFailed) {
            onNotice(message)
            viewModel.noticeShown()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = spacing.lg)) {
            item {
                Text(stringResource(R.string.vault_dedup_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(spacing.md))
            }
            when (val phase = state.phase) {
                DedupPhase.Idle, DedupPhase.Scanning -> item {
                    Progress(stringResource(R.string.vault_dedup_scanning), null)
                }
                is DedupPhase.Backfilling -> item {
                    Progress(
                        stringResource(R.string.vault_dedup_hashing, phase.done, phase.total),
                        phase.takeIf { it.total > 0 }?.let { it.done.toFloat() / it.total },
                    )
                }
                is DedupPhase.Deleting -> item {
                    Progress(
                        stringResource(R.string.vault_dedup_deleting, phase.done, phase.total),
                        phase.done.toFloat() / phase.total,
                    )
                }
                is DedupPhase.Ready -> {
                    item {
                        ModeControls(
                            mode = state.mode,
                            maxDistance = state.maxDistance,
                            onMode = viewModel::setMode,
                            onDistance = viewModel::setMaxDistance,
                            onDistanceCommitted = viewModel::rescan,
                        )
                        Spacer(Modifier.height(spacing.md))
                    }
                    if (phase.groups.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.vault_dedup_none),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    pluralStringResource(
                                        R.plurals.vault_dedup_summary, phase.groups.size,
                                        phase.groups.size, FileSize.format(state.removalBytes),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    enabled = state.removals.isNotEmpty(),
                                    onClick = {
                                        pending = PendingDelete(state.removals, viewModel::applyAll)
                                    },
                                ) {
                                    Text(stringResource(R.string.vault_dedup_apply_all))
                                }
                            }
                        }
                        items(phase.groups, key = { it.keeperUrl }) { group ->
                            GroupRow(
                                group = group,
                                kept = state.keptIn(group),
                                dropping = state.removalsIn(group),
                                onToggle = { viewModel.toggleKept(group, it) },
                                onApply = {
                                    pending = PendingDelete(state.removalsIn(group)) { viewModel.applyGroup(group) }
                                },
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(spacing.xxl)) }
        }
    }

    pending?.let { request ->
        val count = request.entries.size
        val bytes = request.entries.sumOf { it.sizeBytes }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.vault_dedup_confirm_title)) },
            text = {
                Text(pluralStringResource(R.plurals.vault_dedup_confirm_body, count, count, FileSize.format(bytes)))
            },
            confirmButton = {
                TextButton(onClick = { pending = null; request.run() }) {
                    Text(stringResource(R.string.vault_dedup_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

/** A delete waiting on its confirmation dialog: what goes, and the call that does it. */
private class PendingDelete(val entries: List<DuplicateEntry>, val run: () -> Unit)

@Composable
private fun Progress(label: String, fraction: Float?) {
    val spacing = LocalSpacing.current
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(spacing.sm))
        if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(spacing.lg))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeControls(
    mode: DedupMode,
    maxDistance: Int,
    onMode: (DedupMode) -> Unit,
    onDistance: (Int) -> Unit,
    onDistanceCommitted: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(Modifier.fillMaxWidth()) {
        EnumSegmentedRow(DedupMode.entries, selected = mode, onSelect = onMode, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(it.labelRes))
        }
        if (mode == DedupMode.SIMILAR) {
            Spacer(Modifier.height(spacing.sm))
            Text(
                stringResource(R.string.vault_dedup_distance, maxDistance),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = maxDistance.toFloat(),
                onValueChange = { onDistance(it.toInt()) },
                onValueChangeFinished = onDistanceCommitted,
                valueRange = 0f..16f,
                steps = 15,
            )
        }
    }
}

@Composable
private fun GroupRow(
    group: DuplicateGroup,
    kept: Set<String>,
    dropping: List<DuplicateEntry>,
    onToggle: (String) -> Unit,
    onApply: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(Modifier.fillMaxWidth().padding(vertical = spacing.sm)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            items(group.entries, key = { it.url }) { entry ->
                Thumb(entry, selected = entry.url in kept, onClick = { onToggle(entry.url) })
            }
        }
        Spacer(Modifier.height(spacing.xs))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                group.entries.firstOrNull { it.url in kept }?.subject
                    ?: group.entries.first().subject
                    ?: stringResource(R.string.vault_dedup_untitled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            TextButton(onClick = onApply, enabled = dropping.isNotEmpty()) {
                Text(
                    pluralStringResource(
                        R.plurals.vault_dedup_keep_selected, dropping.size,
                        dropping.size, FileSize.format(dropping.sumOf { it.sizeBytes }),
                    ),
                )
            }
        }
    }
}

@Composable
private fun Thumb(entry: DuplicateEntry, selected: Boolean, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    val shape = RoundedCornerShape(THUMB_CORNER)
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.width(THUMB_SIZE).clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(THUMB_SIZE)
                .clip(shape)
                .border(if (selected) 3.dp else 1.dp, outline, shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = when {
                    !entry.isVideo -> File(entry.absolutePath)
                    entry.thumbnailPath != null -> File(entry.thumbnailPath)
                    else -> null
                },
                contentDescription = entry.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(THUMB_SIZE),
            )
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.vault_dedup_kept),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(spacing.xs).size(20.dp),
                )
            }
        }
        val dims = if (entry.width != null && entry.height != null) "${entry.width}×${entry.height}" else "?"
        Text("$dims · ${FileSize.format(entry.sizeBytes)}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(
            TimeFormat.dateShort(entry.savedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private val THUMB_SIZE = 112.dp
private val THUMB_CORNER = 8.dp

private val DedupMode.labelRes: Int
    get() = when (this) {
        DedupMode.EXACT -> R.string.vault_dedup_mode_exact
        DedupMode.SIMILAR -> R.string.vault_dedup_mode_similar
    }

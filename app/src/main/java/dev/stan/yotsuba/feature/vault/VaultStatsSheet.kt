package dev.stan.yotsuba.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.util.FileSize
import dev.stan.yotsuba.domain.model.VaultLocation

/** A full-height sheet reading [stats]. Tapping a thread hands its location to [onOpenThread]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultStatsSheet(
    stats: VaultStats,
    onDismiss: () -> Unit,
    onOpenThread: (VaultLocation) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            item {
                Text(stringResource(R.string.vault_stats_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
            }
            if (stats.isEmpty) {
                item {
                    Text(
                        stringResource(R.string.vault_stats_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(32.dp))
                }
                return@LazyColumn
            }
            item { Totals(stats) }
            item { SectionTitle(stringResource(R.string.vault_stats_per_board)) }
            items(stats.perBoard, key = { it.board }) { BoardBar(it, stats.perBoard.first().bytes) }
            item { SectionTitle(stringResource(R.string.vault_stats_biggest_threads)) }
            items(stats.biggestThreads, key = { it.location }) { ThreadRow(it) { onOpenThread(it.location) } }
            item { SectionTitle(stringResource(R.string.vault_stats_per_week)) }
            item { WeeklyBars(stats.savedPerWeek) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun Totals(stats: VaultStats) {
    Column(Modifier.fillMaxWidth()) {
        TotalsRow(
            R.string.vault_stats_files to stats.files.toString(),
            R.string.vault_stats_size to FileSize.format(stats.bytes),
            R.string.vault_stats_threads to stats.threads.toString(),
        )
        Spacer(Modifier.height(12.dp))
        TotalsRow(
            R.string.vault_stats_images to stats.images.toString(),
            R.string.vault_stats_videos to stats.videos.toString(),
            R.string.vault_stats_boards to stats.boards.toString(),
        )
    }
}

@Composable
private fun TotalsRow(vararg cells: Pair<Int, String>) {
    Row(Modifier.fillMaxWidth()) {
        cells.forEach { (labelRes, value) ->
            Column(Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Spacer(Modifier.height(24.dp))
    Text(text, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
}

/** Bar length is [stat] bytes against [largest], the biggest board, which fills the row. */
@Composable
private fun BoardBar(stat: BoardStat, largest: Long) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("/${stat.board}/", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.vault_stats_board_detail, stat.files, FileSize.format(stat.bytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        WeightedBar(fraction = if (largest > 0) stat.bytes.toFloat() / largest else 0f)
    }
}

/** A track with [fraction] of its width filled; the empty rest stays a Box so the Row keeps its shape. */
@Composable
private fun WeightedBar(fraction: Float, modifier: Modifier = Modifier) {
    val filled = fraction.coerceIn(0f, 1f)
    Row(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (filled > 0f) Box(Modifier.weight(filled).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        if (filled < 1f) Box(Modifier.weight(1f - filled))
    }
}

@Composable
private fun ThreadRow(stat: ThreadStat, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(stat.subject?.takeIf { it.isNotBlank() } ?: stringResource(R.string.vault_stats_untitled), maxLines = 1)
        },
        supportingContent = {
            Text(
                stringResource(
                    R.string.vault_stats_thread_detail,
                    stat.location.board,
                    stat.files,
                    FileSize.format(stat.bytes),
                ),
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** One column per week, oldest on the left, tallest at the busiest week. */
@Composable
private fun WeeklyBars(counts: List<Int>) {
    val peak = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(96.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            counts.forEach { count ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(horizontal = 2.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (count > 0) {
                        Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(count.toFloat() / peak * 0.8f)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.vault_stats_per_week_range, counts.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

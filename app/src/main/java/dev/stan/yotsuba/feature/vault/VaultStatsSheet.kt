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
import androidx.compose.material3.CircularProgressIndicator
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
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.FileSize
import dev.stan.yotsuba.domain.model.VaultLocation
import java.text.DateFormat
import java.util.Date

/**
 * Lazy item key for a thread. Compose stores item keys in the saved-state Bundle, so a
 * [VaultLocation] itself would throw the moment the row composes; the rest of the app keys
 * threads as `board/threadNo` too.
 */
internal fun lazyKey(location: VaultLocation): String = location.board + "/" + location.threadNo

/**
 * A full-height sheet reading [stats], or a spinner while they are still null. Tapping a
 * thread hands its location to [onOpenThread].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultStatsSheet(
    stats: VaultStats?,
    onDismiss: () -> Unit,
    onOpenThread: (VaultLocation) -> Unit,
) {
    val spacing = LocalSpacing.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = spacing.lg)) {
            item {
                Text(stringResource(R.string.vault_stats_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(spacing.md))
            }
            if (stats == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = spacing.xxl), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                return@LazyColumn
            }
            if (stats.isEmpty) {
                item {
                    Text(
                        stringResource(R.string.vault_stats_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(spacing.xxl))
                }
                return@LazyColumn
            }
            item { Totals(stats) }
            item { SectionTitle(stringResource(R.string.vault_stats_per_board)) }
            items(stats.perBoard, key = { it.board }) { BoardBar(it, stats.perBoard.first().sizeBytes) }
            item { SectionTitle(stringResource(R.string.vault_stats_biggest_threads)) }
            items(stats.biggestThreads, key = { lazyKey(it.location) }) { ThreadRow(it) { onOpenThread(it.location) } }
            item { SectionTitle(stringResource(R.string.vault_stats_per_week)) }
            item { WeeklyBars(stats.savedPerWeek) }
            item { SectionTitle(stringResource(R.string.vault_stats_dates)) }
            item { SaveDates(stats) }
            item { Spacer(Modifier.height(spacing.xxl)) }
        }
    }
}

@Composable
private fun Totals(stats: VaultStats) {
    val spacing = LocalSpacing.current
    Column(Modifier.fillMaxWidth()) {
        TotalsRow(
            R.string.vault_stats_files to stats.files.toString(),
            R.string.vault_stats_size to FileSize.format(stats.bytes),
            R.string.vault_stats_threads to stats.threads.toString(),
        )
        Spacer(Modifier.height(spacing.md))
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
    val spacing = LocalSpacing.current
    Spacer(Modifier.height(spacing.xl))
    Text(text, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(spacing.sm))
}

/** Bar length is [section] bytes against [largest], the biggest board, which fills the row. */
@Composable
private fun BoardBar(section: VaultBoardSection, largest: Long) {
    val spacing = LocalSpacing.current
    Column(Modifier.fillMaxWidth().padding(vertical = spacing.xs)) {
        Row(Modifier.fillMaxWidth()) {
            Text("/${section.board}/", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.vault_stats_board_detail, section.entries.size, FileSize.format(section.sizeBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(spacing.xs))
        WeightedBar(fraction = if (largest > 0) section.sizeBytes.toFloat() / largest else 0f)
    }
}

/** A track with [fraction] of its width filled; the empty rest stays a Box so the Row keeps its shape. */
@Composable
private fun WeightedBar(fraction: Float, modifier: Modifier = Modifier) {
    val filled = fraction.coerceIn(0f, 1f)
    Row(
        modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(RoundedCornerShape(BAR_HEIGHT / 2))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (filled > 0f) Box(Modifier.weight(filled).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        if (filled < 1f) Box(Modifier.weight(1f - filled))
    }
}

@Composable
private fun ThreadRow(thread: VaultThreadSection, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(thread.subject?.takeIf { it.isNotBlank() } ?: stringResource(R.string.vault_stats_untitled), maxLines = 1)
        },
        supportingContent = {
            Text(
                stringResource(
                    R.string.vault_stats_thread_detail,
                    thread.location.board,
                    thread.entries.size,
                    FileSize.format(thread.sizeBytes),
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
    val spacing = LocalSpacing.current
    val peak = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(CHART_HEIGHT),
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
        Spacer(Modifier.height(spacing.xs))
        Text(
            stringResource(R.string.vault_stats_per_week_range, counts.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val BAR_HEIGHT = 8.dp
private val CHART_HEIGHT = 96.dp

private val dateFormat: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

@Composable
private fun SaveDates(stats: VaultStats) {
    Row(Modifier.fillMaxWidth()) {
        DateCell(R.string.vault_stats_oldest, stats.oldestSave, Modifier.weight(1f))
        DateCell(R.string.vault_stats_newest, stats.newestSave, Modifier.weight(1f))
    }
}

@Composable
private fun DateCell(labelRes: Int, at: Long?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(at?.let { dateFormat.format(Date(it)) } ?: "\u2014", style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

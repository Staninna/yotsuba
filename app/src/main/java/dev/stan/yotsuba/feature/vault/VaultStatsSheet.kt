package dev.stan.yotsuba.feature.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

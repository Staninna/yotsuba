package dev.stan.yotsuba.feature.vault

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.vault.VaultPaths
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.feature.media.requestAllFilesAccess
import java.io.File

/** The drill-down body of the vault: boards → threads → media grid, plus the empty states. */
@Composable
internal fun VaultExplorer(
    state: VaultUiState,
    onOpenBoard: (String) -> Unit,
    onOpenThread: (VaultLocation) -> Unit,
    onOpenEntry: (VaultEntry) -> Unit,
    onLongPressEntry: (VaultEntry) -> Unit,
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        when {
            !state.hasStorageAccess -> Column(
                Modifier.align(Alignment.Center).padding(spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(
                    stringResource(R.string.vault_grant_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { requestAllFilesAccess(context) }) {
                    Text(stringResource(R.string.vault_grant_button))
                }
            }

            state.entries.isEmpty() -> Column(
                Modifier.align(Alignment.Center).padding(spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.vault_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.vault_empty_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.selection.board == null -> BoardList(state.boards, onOpenBoard)

            state.selection.thread == null ->
                ThreadList(state.openBoard?.threads.orEmpty(), onOpenThread)

            else -> MediaGrid(
                entries = state.openThread?.entries.orEmpty(),
                onOpen = onOpenEntry,
                onLongPress = onLongPressEntry,
            )
        }
    }
}

@Composable
private fun BoardList(boards: List<VaultBoardSection>, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(boards.size, key = { boards[it].board }) { i ->
            val section = boards[i]
            ListItem(
                headlineContent = { Text(boardTitle(section.board)) },
                supportingContent = {
                    val count = section.entries.size
                    Text(pluralStringResource(R.plurals.vault_items, count, count))
                },
                leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                modifier = Modifier.clickable { onOpen(section.board) },
            )
        }
    }
}

@Composable
private fun ThreadList(threads: List<VaultThreadSection>, onOpen: (VaultLocation) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(threads.size, key = { threads[it].location.threadNo }) { i ->
            val section = threads[i]
            ListItem(
                headlineContent = { Text(threadTitle(section.location, section.subject), maxLines = 1) },
                supportingContent = {
                    val count = section.entries.size
                    Text(pluralStringResource(R.plurals.vault_items, count, count))
                },
                leadingContent = { MediaThumb(section.entries.first(), Modifier.size(56.dp)) },
                modifier = Modifier.clickable { onOpen(section.location) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(
    entries: List<VaultEntry>,
    onOpen: (VaultEntry) -> Unit,
    onLongPress: (VaultEntry) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(entries, key = { it.url }) { entry ->
            Box(
                Modifier
                    .aspectRatio(1f)
                    .combinedClickable(
                        onClick = { onOpen(entry) },
                        onLongClick = { onLongPress(entry) },
                    ),
            ) {
                MediaThumb(entry, Modifier.fillMaxSize())
                if (entry.isVideo) {
                    Icon(
                        Icons.Filled.PlayCircle,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun MediaThumb(entry: VaultEntry, modifier: Modifier = Modifier) {
    // Images decode straight from disk; videos fall back to their cached remote thumbnail.
    AsyncImage(
        model = if (entry.isVideo) entry.thumbnailUrl else File(entry.absolutePath),
        contentDescription = entry.displayName,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Shuffle-play FAB over whatever level is on screen: everything, one board, or one thread. */
@Composable
internal fun VaultShuffleFab(scopeEntries: List<VaultEntry>, onShuffle: (List<String>) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        FloatingActionButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.Shuffle, stringResource(R.string.vault_shuffle))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ShuffleMenuItem(R.string.vault_shuffle_everything, Icons.Filled.Shuffle) {
                menuOpen = false
                onShuffle(scopeEntries.map { it.url })
            }
            ShuffleMenuItem(R.string.vault_shuffle_videos, Icons.Filled.Movie) {
                menuOpen = false
                onShuffle(scopeEntries.filter { it.isVideo }.map { it.url })
            }
            ShuffleMenuItem(R.string.vault_shuffle_images, Icons.Filled.Image) {
                menuOpen = false
                onShuffle(scopeEntries.filterNot { it.isVideo }.map { it.url })
            }
        }
    }
}

@Composable
private fun ShuffleMenuItem(labelRes: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
internal fun boardTitle(board: String): String = when (board) {
    VaultPaths.UNSORTED_DIR_NAME -> stringResource(R.string.vault_unsorted)
    else -> "/$board/"
}

/** Thread title in the list and the top bar; unsorted shows its own label instead of "Thread 0". */
@Composable
internal fun threadTitle(location: VaultLocation, subject: String?): String = when {
    location.isUnsorted -> stringResource(R.string.vault_unsorted)
    else -> subject ?: stringResource(R.string.vault_thread_untitled, location.threadNo)
}

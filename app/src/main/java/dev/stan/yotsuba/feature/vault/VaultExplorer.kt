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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import dev.stan.yotsuba.core.util.FileSize
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
    onToggleSelected: (Collection<String>) -> Unit,
    onDeleteThread: (VaultLocation) -> Unit,
    onDeleteBoard: (String) -> Unit,
    onRenameThread: (VaultLocation) -> Unit,
    onMergeThread: (VaultLocation) -> Unit,
    onSort: (VaultSort) -> Unit,
    onFilter: (VaultFilter) -> Unit,
    onMode: (VaultMode) -> Unit,
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

            state.results != null -> Column(Modifier.fillMaxSize()) {
                VaultChipRow(state.sort, state.filter, onSort, onFilter)
                if (state.results.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.vault_search_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    MediaGrid(
                        entries = state.results,
                        selected = state.selected,
                        onOpen = onOpenEntry,
                        onLongPress = onLongPressEntry,
                        onToggleSelected = { onToggleSelected(listOf(it.url)) },
                    )
                }
            }

            state.selection.board == null -> Column(Modifier.fillMaxSize()) {
                ModeSwitch(state.mode, onMode)
                if (state.mode == VaultMode.RECENT) {
                    VaultChipRow(state.sort, state.filter, onSort, onFilter)
                    MediaGrid(
                        entries = state.recent,
                        selected = state.selected,
                        onOpen = onOpenEntry,
                        onLongPress = onLongPressEntry,
                        onToggleSelected = { onToggleSelected(listOf(it.url)) },
                    )
                } else {
                    BoardList(state.boards, onOpenBoard, onDeleteBoard)
                }
            }

            state.selection.thread == null -> ThreadList(
                threads = state.openBoard?.threads.orEmpty(),
                selected = state.selected,
                onOpen = onOpenThread,
                onToggleSelected = onToggleSelected,
                onDelete = onDeleteThread,
                onRename = onRenameThread,
                onMerge = onMergeThread,
            )

            else -> Column(Modifier.fillMaxSize()) {
                VaultChipRow(state.sort, state.filter, onSort, onFilter)
                MediaGrid(
                    entries = state.openThread?.entries.orEmpty(),
                    selected = state.selected,
                    onOpen = onOpenEntry,
                    onLongPress = onLongPressEntry,
                    onToggleSelected = { onToggleSelected(listOf(it.url)) },
                )
            }
        }
    }
}

@Composable
private fun BoardList(
    boards: List<VaultBoardSection>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(boards.size, key = { boards[it].board }) { i ->
            val section = boards[i]
            ListItem(
                headlineContent = { Text(boardTitle(section.board)) },
                supportingContent = {
                    val count = section.entries.size
                    Text(
                        pluralStringResource(R.plurals.vault_items, count, count) +
                            " · " + FileSize.format(section.sizeBytes),
                    )
                },
                leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                trailingContent = {
                    OverflowMenu {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.vault_delete_board)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { it(); onDelete(section.board) },
                        )
                    }
                },
                modifier = Modifier.clickable { onOpen(section.board) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadList(
    threads: List<VaultThreadSection>,
    selected: Set<String>,
    onOpen: (VaultLocation) -> Unit,
    onToggleSelected: (Collection<String>) -> Unit,
    onDelete: (VaultLocation) -> Unit,
    onRename: (VaultLocation) -> Unit,
    onMerge: (VaultLocation) -> Unit,
) {
    val selecting = selected.isNotEmpty()
    LazyColumn(Modifier.fillMaxSize()) {
        items(threads.size, key = { threads[it].location.threadNo }) { i ->
            val section = threads[i]
            val urls = section.entries.map { it.url }
            val checked = selecting && selected.containsAll(urls)
            ListItem(
                headlineContent = { Text(threadTitle(section.location, section.subject), maxLines = 1) },
                supportingContent = {
                    val count = section.entries.size
                    Text(
                        pluralStringResource(R.plurals.vault_items, count, count) +
                            " · " + FileSize.format(section.sizeBytes),
                    )
                },
                leadingContent = {
                    if (selecting) {
                        Checkbox(checked = checked, onCheckedChange = { onToggleSelected(urls) })
                    } else {
                        MediaThumb(section.entries.first(), Modifier.size(56.dp))
                    }
                },
                trailingContent = {
                    if (!selecting) {
                        OverflowMenu {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.vault_select)) },
                                leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                                onClick = { it(); onToggleSelected(urls) },
                            )
                            if (section.location.isLocal) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.vault_rename_thread)) },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = { it(); onRename(section.location) },
                                )
                            }
                            if (!section.location.isUnsorted && threads.size > 1) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.vault_merge_thread)) },
                                    leadingIcon = { Icon(Icons.Filled.CallMerge, contentDescription = null) },
                                    onClick = { it(); onMerge(section.location) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.vault_delete_thread)) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = { it(); onDelete(section.location) },
                            )
                        }
                    }
                },
                modifier = Modifier
                    .background(
                        if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    )
                    .combinedClickable(
                        onClick = { if (selecting) onToggleSelected(urls) else onOpen(section.location) },
                        onLongClick = { onToggleSelected(urls) },
                    ),
            )
        }
    }
}

/** Recent feed or the board drill-down, at the root only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSwitch(mode: VaultMode, onMode: (VaultMode) -> Unit) {
    val spacing = LocalSpacing.current
    SingleChoiceSegmentedButtonRow(
        Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.xs),
    ) {
        VaultMode.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onMode(option) },
                shape = SegmentedButtonDefaults.itemShape(index, VaultMode.entries.size),
            ) {
                Text(
                    stringResource(
                        when (option) {
                            VaultMode.RECENT -> R.string.vault_mode_recent
                            VaultMode.BROWSE -> R.string.vault_mode_browse
                        },
                    ),
                )
            }
        }
    }
}

/** Sort and type filter, one row of chips, above every grid. The sort chip cycles through a menu. */
@Composable
internal fun VaultChipRow(
    sort: VaultSort,
    filter: VaultFilter,
    onSort: (VaultSort) -> Unit,
    onFilter: (VaultFilter) -> Unit,
    onMode: (VaultMode) -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        var sortMenu by remember { mutableStateOf(false) }
        Box {
            FilterChip(
                selected = true,
                onClick = { sortMenu = true },
                label = { Text(stringResource(sortLabel(sort))) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            )
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                VaultSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sortLabel(option))) },
                        onClick = { sortMenu = false; onSort(option) },
                    )
                }
            }
        }
        VaultFilter.entries.forEach { option ->
            FilterChip(
                selected = filter == option,
                onClick = { onFilter(option) },
                label = { Text(stringResource(filterLabel(option))) },
            )
        }
    }
}

private fun sortLabel(sort: VaultSort): Int = when (sort) {
    VaultSort.SAVED -> R.string.vault_sort_saved
    VaultSort.SIZE -> R.string.vault_sort_size
    VaultSort.NAME -> R.string.vault_sort_name
    VaultSort.POST -> R.string.vault_sort_post
}

private fun filterLabel(filter: VaultFilter): Int = when (filter) {
    VaultFilter.ALL -> R.string.vault_filter_all
    VaultFilter.IMAGES -> R.string.vault_filter_images
    VaultFilter.VIDEOS -> R.string.vault_filter_videos
}

/** A three-dot button and its menu; items call the passed closer before acting. */
@Composable
internal fun OverflowMenu(items: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.vault_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items { open = false }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(
    entries: List<VaultEntry>,
    selected: Set<String>,
    onOpen: (VaultEntry) -> Unit,
    onLongPress: (VaultEntry) -> Unit,
    onToggleSelected: (VaultEntry) -> Unit,
) {
    val selecting = selected.isNotEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(entries, key = { it.url }) { entry ->
            val checked = entry.url in selected
            Box(
                Modifier
                    .aspectRatio(1f)
                    .combinedClickable(
                        onClick = { if (selecting) onToggleSelected(entry) else onOpen(entry) },
                        onLongClick = { onLongPress(entry) },
                    ),
            ) {
                MediaThumb(
                    entry,
                    Modifier.fillMaxSize().then(if (checked) Modifier.padding(6.dp) else Modifier),
                )
                if (entry.isVideo) {
                    Icon(
                        Icons.Filled.PlayCircle,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                    )
                }
                if (selecting) {
                    Icon(
                        if (checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = null,
                        tint = if (checked) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun MediaThumb(entry: VaultEntry, modifier: Modifier = Modifier) {
    // Images decode straight from disk; videos show their local still, or the cached
    // remote thumbnail for one saved before stills existed and not yet rescanned.
    AsyncImage(
        model = when {
            !entry.isVideo -> File(entry.absolutePath)
            entry.localThumbnailPath != null -> File(entry.localThumbnailPath!!)
            else -> entry.thumbnailUrl
        },
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
    VaultPaths.LOCAL_BOARD_NAME -> stringResource(R.string.vault_local_board)
    else -> "/$board/"
}

/** Thread title in the list and the top bar; unsorted shows its own label instead of "Thread 0". */
@Composable
internal fun threadTitle(location: VaultLocation, subject: String?): String = when {
    location.isUnsorted -> stringResource(R.string.vault_unsorted)
    else -> subject ?: stringResource(R.string.vault_thread_untitled, location.threadNo)
}

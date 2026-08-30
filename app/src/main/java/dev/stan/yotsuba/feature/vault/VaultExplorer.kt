package dev.stan.yotsuba.feature.vault

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.EmptyState
import dev.stan.yotsuba.core.designsystem.component.LoadingSkeleton
import dev.stan.yotsuba.core.designsystem.rememberHaptics
import dev.stan.yotsuba.core.designsystem.rememberMotionSpec
import dev.stan.yotsuba.core.designsystem.token.LocalMotion
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.FileSize
import dev.stan.yotsuba.core.vault.VaultPaths
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.feature.media.requestAllFilesAccess
import java.io.File
import java.text.DateFormat
import java.util.Date

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
    onToggleReversed: () -> Unit,
    onFilter: (VaultFilter) -> Unit,
    onMode: (VaultMode) -> Unit,
) {
    val context = LocalContext.current
    // Sort, direction and filter reorder every list; each one scrolls back to the top when
    // they change instead of chasing whichever key happened to be first on screen.
    val view = remember(state.sort, state.reversed, state.filter) {
        VaultArrangement(state.sort, state.reversed, state.filter)
    }
    val grid: @Composable (entries: List<VaultEntry>, emptyText: String) -> Unit = { entries, emptyText ->
        Column(Modifier.fillMaxSize()) {
            VaultChipRow(state.sort, state.reversed, state.filter, onSort, onToggleReversed, onFilter)
            MediaGrid(
                view = view,
                entries = entries,
                emptyText = emptyText,
                selected = state.selected,
                onOpen = onOpenEntry,
                onLongPress = onLongPressEntry,
                onToggleSelected = { onToggleSelected(listOf(it.url)) },
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
        when (val body = state.body) {
            VaultBody.Loading -> LoadingSkeleton()

            VaultBody.NoAccess -> EmptyState(
                title = stringResource(R.string.vault_grant_button),
                explanation = stringResource(R.string.vault_grant_explanation),
                icon = Icons.Filled.FolderOff,
            ) {
                Button(onClick = { requestAllFilesAccess(context) }) {
                    Text(stringResource(R.string.vault_grant_button))
                }
            }

            VaultBody.Empty -> EmptyState(
                title = stringResource(R.string.vault_empty_title),
                explanation = stringResource(R.string.vault_empty_explanation),
                icon = Icons.Filled.PermMedia,
            )

            is VaultBody.Grid -> grid(
                body.entries,
                stringResource(if (body.searching) R.string.vault_search_empty else R.string.vault_filter_empty),
            )

            is VaultBody.Root -> Column(Modifier.fillMaxSize()) {
                ModeSwitch(state.mode, onMode)
                Crossfade(
                    targetState = state.mode,
                    animationSpec = rememberMotionSpec(LocalMotion.current.medium),
                    label = "vaultMode",
                    modifier = Modifier.fillMaxSize(),
                ) { mode ->
                    if (mode == VaultMode.RECENT) {
                        grid(body.recent, stringResource(R.string.vault_filter_empty))
                    } else {
                        BoardList(view, state.boards, onOpenBoard, onDeleteBoard)
                    }
                }
            }

            is VaultBody.Threads -> ThreadList(
                view = view,
                threads = body.threads,
                selected = state.selected,
                onOpen = onOpenThread,
                onToggleSelected = onToggleSelected,
                onDelete = onDeleteThread,
                onRename = onRenameThread,
                onMerge = onMergeThread,
            )
        }
    }
}

@Composable
private fun BoardList(
    view: VaultArrangement,
    boards: List<VaultBoardSection>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(view) { listState.scrollToItem(0) }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        items(boards.size, key = { boards[it].board }) { i ->
            val section = boards[i]
            ListItem(
                headlineContent = { Text(boardTitle(section.board)) },
                supportingContent = { Text(itemsSummary(section.entries.size, section.sizeBytes)) },
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
                // No animateItem(): an arrangement change already jumps the list to the top.
                modifier = Modifier.clickable { onOpen(section.board) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadList(
    view: VaultArrangement,
    threads: List<VaultThreadSection>,
    selected: Set<String>,
    onOpen: (VaultLocation) -> Unit,
    onToggleSelected: (Collection<String>) -> Unit,
    onDelete: (VaultLocation) -> Unit,
    onRename: (VaultLocation) -> Unit,
    onMerge: (VaultLocation) -> Unit,
) {
    val selecting = selected.isNotEmpty()
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()
    LaunchedEffect(view) { listState.scrollToItem(0) }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        items(threads.size, key = { threads[it].location.threadNo }) { i ->
            val section = threads[i]
            val urls = section.entries.map { it.url }
            val checked = selecting && selected.containsAll(urls)
            ListItem(
                headlineContent = { Text(threadTitle(section.location, section.subject), maxLines = 1) },
                supportingContent = {
                    Text(itemsSummary(section.entries.size, section.sizeBytes) + " · " + savedDate(section.savedAt))
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
                // No animateItem(): an arrangement change already jumps the list to the top.
                modifier = Modifier
                    .background(
                        if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    )
                    .selectable(selecting, checked)
                    .combinedClickable(
                        onClick = { if (selecting) onToggleSelected(urls) else onOpen(section.location) },
                        onLongClick = {
                            if (!selecting) haptics.confirm()
                            onToggleSelected(urls)
                        },
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

/**
 * Sort and type filter above every grid, sized to fit a phone width: one sort chip whose
 * trailing arrow shows the direction and whose menu holds the sorts plus "Reverse order",
 * and one icon-only segmented toggle for the media type. The scroll is a safety net for
 * very large fonts only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultChipRow(
    sort: VaultSort,
    reversed: Boolean,
    filter: VaultFilter,
    onSort: (VaultSort) -> Unit,
    onToggleReversed: () -> Unit,
    onFilter: (VaultFilter) -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var sortMenu by remember { mutableStateOf(false) }
        Box {
            FilterChip(
                selected = true,
                onClick = { sortMenu = true },
                label = { Text(stringResource(sortLabel(sort))) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                trailingIcon = {
                    Icon(
                        if (reversed) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                        contentDescription = stringResource(
                            if (reversed) R.string.vault_sort_reversed else R.string.vault_sort_forward,
                        ),
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                VaultSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sortLabel(option))) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = if (option == sort) LocalContentColor.current else Color.Transparent,
                            )
                        },
                        onClick = { sortMenu = false; onSort(option) },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vault_sort_reverse_order)) },
                    leadingIcon = { Icon(Icons.Filled.SwapVert, contentDescription = null) },
                    trailingIcon = { Checkbox(checked = reversed, onCheckedChange = null) },
                    onClick = { sortMenu = false; onToggleReversed() },
                )
            }
        }
        SingleChoiceSegmentedButtonRow {
            VaultFilter.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = filter == option,
                    onClick = { onFilter(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, VaultFilter.entries.size),
                    icon = {},
                    label = {
                        Icon(
                            when (option) {
                                VaultFilter.ALL -> Icons.Filled.PermMedia
                                VaultFilter.IMAGES -> Icons.Filled.Image
                                VaultFilter.VIDEOS -> Icons.Filled.Movie
                            },
                            contentDescription = stringResource(filterLabel(option)),
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    },
                )
            }
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
    view: VaultArrangement,
    entries: List<VaultEntry>,
    /** Shown instead of the grid when nothing is in it: the filter or the search emptied it. */
    emptyText: String,
    selected: Set<String>,
    onOpen: (VaultEntry) -> Unit,
    onLongPress: (VaultEntry) -> Unit,
    onToggleSelected: (VaultEntry) -> Unit,
) {
    val selecting = selected.isNotEmpty()
    val haptics = rememberHaptics()
    val gridState = rememberLazyGridState()
    LaunchedEffect(view) { gridState.scrollToItem(0) }
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val spacing = LocalSpacing.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GRID_CELL_MIN),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(GRID_GUTTER),
        horizontalArrangement = Arrangement.spacedBy(GRID_GUTTER),
    ) {
        items(entries, key = { it.url }) { entry ->
            val checked = entry.url in selected
            // No animateItem(): an arrangement change already jumps the grid to the top.
            Box(
                Modifier
                    .aspectRatio(1f)
                    .selectable(selecting, checked)
                    .combinedClickable(
                        onClick = { if (selecting) onToggleSelected(entry) else onOpen(entry) },
                        onLongClick = {
                            if (!selecting) haptics.confirm()
                            onLongPress(entry)
                        },
                    ),
            ) {
                MediaThumb(
                    entry,
                    Modifier
                        .fillMaxSize()
                        .then(if (checked) Modifier.padding(SELECTED_INSET) else Modifier),
                )
                if (entry.isVideo) {
                    Icon(
                        Icons.Filled.PlayCircle,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.Center).size(spacing.xxl),
                    )
                    entry.durationMs?.let { duration ->
                        Text(
                            formatDuration(duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(spacing.xs)
                                .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall)
                                .padding(horizontal = spacing.xs, vertical = 1.dp),
                        )
                    }
                }
                if (selecting) {
                    Icon(
                        if (checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = null,
                        tint = if (checked) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(spacing.xs).size(SELECT_TICK),
                    )
                }
            }
        }
    }
}

private val GRID_CELL_MIN = 110.dp
private val GRID_GUTTER = 2.dp
/** How far a ticked thumbnail shrinks inside its cell. */
private val SELECTED_INSET = 6.dp
private val SELECT_TICK = 22.dp

/**
 * Read by TalkBack as a ticked or unticked checkbox while selecting; nothing otherwise, so
 * the row or cell announces as the plain clickable it is. Kept beside combinedClickable
 * rather than swapping to toggleable, which has no long press.
 */
private fun Modifier.selectable(selecting: Boolean, checked: Boolean): Modifier =
    if (!selecting) this else semantics {
        role = Role.Checkbox
        selected = checked
    }

/** "N items · 12 MB", the count-and-size line under boards, threads and the top bar. */
@Composable
internal fun itemsSummary(count: Int, bytes: Long): String =
    pluralStringResource(R.plurals.vault_items, count, count) + " · " + FileSize.format(bytes)

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

/** `m:ss`, or `h:mm:ss` past the hour. */
internal fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private val dateFormat: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

internal fun savedDate(millis: Long): String = dateFormat.format(Date(millis))

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

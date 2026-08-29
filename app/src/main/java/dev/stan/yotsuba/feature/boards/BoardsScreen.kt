package dev.stan.yotsuba.feature.boards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.EmptyState
import dev.stan.yotsuba.core.designsystem.component.NoSearchResults
import dev.stan.yotsuba.core.designsystem.component.SearchField
import dev.stan.yotsuba.core.designsystem.component.SectionHeader
import dev.stan.yotsuba.core.designsystem.component.TabChrome
import dev.stan.yotsuba.core.designsystem.component.TabScaffoldSlots
import dev.stan.yotsuba.core.designsystem.component.UiStateContent
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.domain.model.Board

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardsScreen(
    slots: TabScaffoldSlots,
    onOpenBoard: (String) -> Unit,
    viewModel: BoardsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    TabChrome(
        slots = slots,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_boards)) },
                actions = {
                    val editing = (state as? UiState.Success)?.data?.editMode == true
                    IconButton(onClick = viewModel::onToggleEditMode) {
                        Icon(
                            if (editing) Icons.Filled.Check else Icons.Filled.Edit,
                            contentDescription = stringResource(
                                if (editing) R.string.boards_done_editing else R.string.boards_edit_visibility
                            ),
                        )
                    }
                },
            )
        },
    )
    Box(Modifier.fillMaxSize()) {
        UiStateContent(state, onRetry = { viewModel.load(forceRefresh = true) }) { s ->
            if (s.isEmpty && s.searchQuery.isBlank() && !s.editMode) {
                EmptyState(
                    title = stringResource(R.string.boards_empty_title),
                    explanation = stringResource(R.string.boards_empty_explanation),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        SearchField(
                            value = s.searchQuery,
                            onValueChange = viewModel::onSearchChange,
                            hintRes = R.string.boards_search_hint,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.lg, vertical = spacing.sm),
                        )
                    }
                    if (s.favourites.isNotEmpty() && !s.editMode) {
                        item {
                            SectionHeader(stringResource(R.string.boards_favourites))
                        }
                        items(s.favourites.size, key = { "fav_" + s.favourites[it].code }) { i ->
                            BoardRow(
                                board = s.favourites[i],
                                favourite = true,
                                editMode = false,
                                visible = true,
                                onClick = { onOpenBoard(s.favourites[i].code) },
                                onToggleFavourite = { viewModel.onToggleFavourite(s.favourites[i].code) },
                                onToggleVisible = {},
                            )
                        }
                    }
                    if (s.searchQuery.isNotBlank() && s.favourites.isEmpty() && s.sections.isEmpty()) {
                        item(key = "no_results") {
                            NoSearchResults(s.searchQuery, Modifier.fillParentMaxHeight(0.7f))
                        }
                    }
                    s.sections.forEach { section ->
                        item(key = "cat_" + section.category.name) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                SectionHeader(section.category.label, Modifier.weight(1f))
                                if (s.editMode) {
                                    TriStateCheckbox(
                                        state = when (section.allVisible) {
                                            true -> ToggleableState.On
                                            false -> ToggleableState.Off
                                            null -> ToggleableState.Indeterminate
                                        },
                                        onClick = { viewModel.onToggleCategoryVisible(section.category) },
                                        modifier = Modifier.padding(end = spacing.lg),
                                    )
                                }
                            }
                        }
                        items(section.boards.size, key = { section.boards[it].board.code }) { i ->
                            val row = section.boards[i]
                            val code = row.board.code
                            BoardRow(
                                board = row.board,
                                favourite = row.favourite,
                                editMode = s.editMode,
                                visible = row.visible,
                                onClick = { if (!s.editMode) onOpenBoard(code) },
                                onToggleFavourite = { viewModel.onToggleFavourite(code) },
                                onToggleVisible = { viewModel.onToggleBoardVisible(code) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardRow(
    board: Board,
    favourite: Boolean,
    editMode: Boolean,
    visible: Boolean,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleVisible: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (editMode) onToggleVisible else onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
    ) {
        if (editMode) {
            Checkbox(checked = visible, onCheckedChange = { onToggleVisible() })
            Spacer(Modifier.width(spacing.sm))
        }
        SuggestionChip(onClick = onClick, label = { Text("/${board.code}/") }, enabled = !editMode)
        Spacer(Modifier.width(spacing.md))
        Column(Modifier.weight(1f)) {
            Text(board.title, style = MaterialTheme.typography.titleSmall)
            if (board.description.isNotBlank()) {
                Text(
                    board.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (!board.worksafe) {
            Text(
                stringResource(R.string.boards_nsfw),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
            )
            Spacer(Modifier.width(spacing.sm))
        }
        if (!editMode) {
            IconButton(onClick = onToggleFavourite) {
                Icon(
                    if (favourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(R.string.boards_favourite_toggle),
                    tint = if (favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

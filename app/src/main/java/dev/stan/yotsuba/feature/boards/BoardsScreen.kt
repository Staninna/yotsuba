package dev.stan.yotsuba.feature.boards

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import dev.stan.yotsuba.core.designsystem.component.ErrorState
import dev.stan.yotsuba.core.designsystem.component.LoadingSkeleton
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.domain.model.Board

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardsScreen(
    onOpenBoard: (String) -> Unit,
    viewModel: BoardsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_boards)) },
                actions = {
                    val editing = (state as? BoardsUiState.Success)?.editMode == true
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
    ) { padding ->
        when (val s = state) {
            BoardsUiState.Loading -> LoadingSkeleton(Modifier.padding(padding))
            is BoardsUiState.Error -> ErrorState(s.error, onRetry = { viewModel.load(forceRefresh = true) }, Modifier.padding(padding))
            is BoardsUiState.Success -> {
                if (s.isEmpty && s.searchQuery.isBlank() && !s.editMode) {
                    EmptyState(
                        title = stringResource(R.string.boards_empty_title),
                        explanation = stringResource(R.string.boards_empty_explanation),
                        modifier = Modifier.padding(padding),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                        item {
                            OutlinedTextField(
                                value = s.searchQuery,
                                onValueChange = viewModel::onSearchChange,
                                placeholder = { Text(stringResource(R.string.boards_search_hint)) },
                                singleLine = true,
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
                                EmptyState(
                                    title = stringResource(R.string.search_no_results_title),
                                    explanation = stringResource(
                                        R.string.search_no_results_explanation, s.searchQuery,
                                    ),
                                    modifier = Modifier.fillParentMaxHeight(0.7f),
                                )
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
                                            onClick = {
                                                viewModel.onToggleCategoryVisible(section.category, section.allVisible)
                                            },
                                            modifier = Modifier.padding(end = spacing.lg),
                                        )
                                    }
                                }
                            }
                            items(section.boards.size, key = { section.boards[it].code }) { i ->
                                val board = section.boards[i]
                                BoardRow(
                                    board = board,
                                    favourite = board.code in s.favouriteBoardCodes,
                                    editMode = s.editMode,
                                    visible = board.code !in s.hiddenBoards &&
                                        board.category.name !in s.hiddenCategories,
                                    onClick = { if (!s.editMode) onOpenBoard(board.code) },
                                    onToggleFavourite = { viewModel.onToggleFavourite(board.code) },
                                    onToggleVisible = { viewModel.onToggleBoardVisible(board.code) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
    )
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
            AssistChip(onClick = {}, label = { Text(stringResource(R.string.boards_nsfw)) })
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

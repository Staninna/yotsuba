package dev.stan.yotsuba.feature.boards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.LoadableFlow
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BoardsViewModel @Inject constructor(
    private val boardRepository: BoardRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val boardsResult = LoadableFlow(viewModelScope) { boardRepository.boards(it) }
    private val searchQuery = MutableStateFlow("")
    private val editMode = MutableStateFlow(false)

    init { load() }

    fun load(forceRefresh: Boolean = false) {
        boardsResult.load(forceRefresh, showLoading = true)
    }

    val uiState: StateFlow<UiState<BoardsContent>> = combine(
        boardsResult.flow, settingsRepository.settings, searchQuery, editMode,
    ) { result, settings, query, editing ->
        when (result) {
            null -> UiState.Loading
            is DataResult.Failure -> UiState.Error(result.error)
            is DataResult.Success -> {
                val all = result.value
                val matching = if (query.isBlank()) all else all.filter {
                    it.code.contains(query, true) || it.title.contains(query, true)
                }
                val visible = matching.filter { editing || settings.isVisible(it) }
                val sections = BoardCategory.entries.mapNotNull { cat ->
                    val inCat = visible.filter { it.category == cat }
                    if (inCat.isEmpty()) return@mapNotNull null
                    BoardSection(
                        category = cat,
                        boards = inCat.map { board ->
                            BoardRowState(
                                board = board,
                                favourite = board.code in settings.favouriteBoards,
                                visible = settings.isVisible(board),
                            )
                        },
                        allVisible = settings.allVisible(cat, all),
                    )
                }
                UiState.Success(BoardsContent(
                    favourites = visible.filter { it.code in settings.favouriteBoards },
                    sections = sections,
                    searchQuery = query,
                    editMode = editing,
                ))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun onSearchChange(query: String) { searchQuery.value = query }
    fun onToggleEditMode() { editMode.value = !editMode.value }

    fun onToggleFavourite(board: String) = viewModelScope.launch {
        settingsRepository.update { s ->
            s.copy(
                favouriteBoards = if (board in s.favouriteBoards) {
                    s.favouriteBoards - board
                } else {
                    s.favouriteBoards + board
                }
            )
        }
    }

    fun onToggleBoardVisible(board: String) = viewModelScope.launch {
        val all = loadedBoards()
        val category = all.firstOrNull { it.code == board }?.category
        settingsRepository.update { s ->
            if (category != null && category.name in s.hiddenCategories) {
                // The whole category is hidden, so the user wants only this board back:
                // unhide the category and hide every sibling instead.
                val siblings = all.filter { it.category == category && it.code != board }.map { it.code }
                s.copy(
                    hiddenCategories = s.hiddenCategories - category.name,
                    hiddenBoards = s.hiddenBoards - board + siblings,
                )
            } else {
                s.copy(
                    hiddenBoards = if (board in s.hiddenBoards) s.hiddenBoards - board else s.hiddenBoards + board,
                )
            }
        }
    }

    fun onToggleCategoryVisible(category: BoardCategory) = viewModelScope.launch {
        val all = loadedBoards()
        val boards = all.filter { it.category == category }.map { it.code }
        settingsRepository.update { s ->
            // Mixed or all-visible -> hide all; hidden -> show all (tri-state, D13).
            if (s.allVisible(category, all) == false) {
                s.copy(
                    hiddenCategories = s.hiddenCategories - category.name,
                    hiddenBoards = s.hiddenBoards - boards.toSet(),
                )
            } else {
                s.copy(
                    hiddenCategories = s.hiddenCategories + category.name,
                )
            }
        }
    }

    private fun loadedBoards(): List<Board> =
        (boardsResult.current as? DataResult.Success)?.value.orEmpty()

    private fun Settings.isVisible(board: Board): Boolean =
        board.code !in hiddenBoards && board.category.name !in hiddenCategories

    /** true = every board shown, false = none, null = mixed. */
    private fun Settings.allVisible(category: BoardCategory, all: List<Board>): Boolean? {
        val inCat = all.filter { it.category == category }
        return when (inCat.count { isVisible(it) }) {
            inCat.size -> true
            0 -> false
            else -> null
        }
    }
}

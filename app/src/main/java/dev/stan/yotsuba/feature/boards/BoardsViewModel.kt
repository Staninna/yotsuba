package dev.stan.yotsuba.feature.boards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory
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

    private val boardsResult = MutableStateFlow<DataResult<List<Board>>?>(null)
    private val searchQuery = MutableStateFlow("")
    private val editMode = MutableStateFlow(false)

    init { load() }

    fun load(forceRefresh: Boolean = false) {
        boardsResult.value = null
        viewModelScope.launch {
            boardsResult.value = boardRepository.boards(forceRefresh)
        }
    }

    val uiState: StateFlow<BoardsUiState> = combine(
        boardsResult, settingsRepository.settings, searchQuery, editMode,
    ) { result, settings, query, editing ->
        when (result) {
            null -> BoardsUiState.Loading
            is DataResult.Failure -> BoardsUiState.Error(result.error)
            is DataResult.Success -> {
                val all = result.value
                val matching = if (query.isBlank()) all else all.filter {
                    it.code.contains(query, true) || it.title.contains(query, true)
                }
                val visible = matching.filter { board ->
                    editing || (board.code !in settings.hiddenBoards &&
                        board.category.name !in settings.hiddenCategories)
                }
                val sections = BoardCategory.entries.mapNotNull { cat ->
                    val inCat = visible.filter { it.category == cat }
                    if (inCat.isEmpty()) return@mapNotNull null
                    val fullCat = all.filter { it.category == cat }
                    val visibleCount = fullCat.count {
                        it.code !in settings.hiddenBoards && cat.name !in settings.hiddenCategories
                    }
                    BoardSection(
                        category = cat,
                        boards = inCat,
                        allVisible = when (visibleCount) {
                            fullCat.size -> true
                            0 -> false
                            else -> null
                        },
                    )
                }
                BoardsUiState.Success(
                    favourites = visible.filter { it.code in settings.favouriteBoards },
                    sections = sections,
                    searchQuery = query,
                    editMode = editing,
                    hiddenBoards = settings.hiddenBoards,
                    hiddenCategories = settings.hiddenCategories,
                    favouriteBoardCodes = settings.favouriteBoards,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardsUiState.Loading)

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
        settingsRepository.update { s ->
            s.copy(
                hiddenBoards = if (board in s.hiddenBoards) s.hiddenBoards - board else s.hiddenBoards + board,
            )
        }
    }

    fun onToggleCategoryVisible(category: BoardCategory, currentlyAllVisible: Boolean?) =
        viewModelScope.launch {
            val boards = (boardsResult.value as? DataResult.Success)?.value.orEmpty()
                .filter { it.category == category }.map { it.code }
            settingsRepository.update { s ->
                // Mixed or all-visible -> hide all; hidden -> show all (tri-state, D13).
                if (currentlyAllVisible == false) {
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
}

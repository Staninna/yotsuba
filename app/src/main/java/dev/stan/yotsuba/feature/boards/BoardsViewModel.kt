package dev.stan.yotsuba.feature.boards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.LoadableFlow
import dev.stan.yotsuba.core.util.UiState
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
                val visibility = settings.visibility()
                val matching = search(all, query)
                val visible = matching.filter { editing || visibility.isVisible(it) }
                // A query sorts the categories by their best match too, so /g/ comes before
                // every earlier category whose titles merely contain a g.
                val order = if (query.isBlank()) BoardCategory.entries else visible.map { it.category }.distinct()
                val sections = order.mapNotNull { cat ->
                    val inCat = visible.filter { it.category == cat }
                    if (inCat.isEmpty()) return@mapNotNull null
                    BoardSection(
                        category = cat,
                        boards = inCat.map { board ->
                            BoardRowState(
                                board = board,
                                favourite = board.code in settings.favouriteBoards,
                                visible = visibility.isVisible(board),
                            )
                        },
                        allVisible = visibility.state(cat, all),
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

    fun addFavourite(board: String) = viewModelScope.launch {
        settingsRepository.update { s -> s.copy(favouriteBoards = s.favouriteBoards + board) }
    }

    /**
     * Drops [board] from the favourites and returns an undo that puts it back in its old
     * position rather than at the end, the same restore Home's tab strip uses.
     */
    fun removeFavourite(board: String): () -> Unit {
        var before: Set<String> = emptySet()
        viewModelScope.launch {
            settingsRepository.update { s ->
                before = s.favouriteBoards
                s.copy(favouriteBoards = s.favouriteBoards - board)
            }
        }
        return {
            viewModelScope.launch {
                settingsRepository.update { s ->
                    // Keep anything favourited in the meantime, but restore the old order.
                    s.copy(favouriteBoards = before + (s.favouriteBoards - before))
                }
            }
        }
    }

    fun onToggleBoardVisible(board: String) = viewModelScope.launch {
        val all = loadedBoards()
        settingsRepository.update { it.visibility().toggleBoard(board, all).into(it) }
    }

    fun onToggleCategoryVisible(category: BoardCategory) = viewModelScope.launch {
        val all = loadedBoards()
        settingsRepository.update { it.visibility().toggleCategory(category, all).into(it) }
    }

    /**
     * Code matches outrank title matches, and an exact code outranks a prefix, so "g" puts /g/
     * first instead of every title with a g in it. Slashes are stripped so "/g/" matches too.
     */
    private fun search(all: List<Board>, rawQuery: String): List<Board> {
        val query = rawQuery.trim().trim('/').trim()
        if (query.isBlank()) return all
        fun rank(board: Board): Int = when {
            board.code.equals(query, ignoreCase = true) -> 0
            board.code.startsWith(query, ignoreCase = true) -> 1
            board.code.contains(query, ignoreCase = true) -> 2
            board.title.contains(query, ignoreCase = true) -> 3
            else -> -1
        }
        return all.map { it to rank(it) }
            .filter { (_, r) -> r >= 0 }
            .sortedBy { (_, r) -> r }
            .map { (board, _) -> board }
    }

    private fun loadedBoards(): List<Board> =
        (boardsResult.current as? DataResult.Success)?.value.orEmpty()
}

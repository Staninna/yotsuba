package dev.stan.yotsuba.feature.boards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.stan.yotsuba.core.util.LoadableFlow
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
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

    init {
        load()
        migrateHiddenCategories()
    }

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
                val hidden = settings.hiddenBoards
                val matching = search(all, query)
                val visible = matching.filter { editing || it.code !in hidden }
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
                                visible = board.code !in hidden,
                            )
                        },
                        allVisible = categoryState(cat, all, hidden),
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
        settingsRepository.update { s ->
            s.copy(hiddenBoards = if (board in s.hiddenBoards) s.hiddenBoards - board else s.hiddenBoards + board)
        }
    }

    /**
     * A header toggle acts on the boards currently in the category: every one hidden shows
     * them all, anything else hides them all (tri-state, D13). A board 4chan adds to the
     * category later is shown until the user hides it.
     */
    fun onToggleCategoryVisible(category: BoardCategory) = viewModelScope.launch {
        val all = loadedBoards()
        val codes = all.filter { it.category == category }.map { it.code }.toSet()
        settingsRepository.update { s ->
            val hiddenBoards = if (categoryState(category, all, s.hiddenBoards) == false) {
                s.hiddenBoards - codes
            } else {
                s.hiddenBoards + codes
            }
            s.copy(hiddenBoards = hiddenBoards)
        }
    }

    /** true = every board in [category] shown, false = none, null = mixed. */
    private fun categoryState(category: BoardCategory, all: List<Board>, hidden: Set<String>): Boolean? {
        val inCat = all.filter { it.category == category }
        return when (inCat.count { it.code !in hidden }) {
            inCat.size -> true
            0 -> false
            else -> null
        }
    }

    /**
     * Older installs hid whole categories by name. Once the board list is in, fold those
     * boards into [Settings.hiddenBoards] and clear the legacy set, in one write.
     */
    private fun migrateHiddenCategories() = viewModelScope.launch {
        if (settingsRepository.settings.first().hiddenCategories.isEmpty()) return@launch
        val all = boardsResult.flow.filterIsInstance<DataResult.Success<List<Board>>>().first().value
        settingsRepository.update { s ->
            val codes = all.filter { it.category.name in s.hiddenCategories }.map { it.code }
            s.copy(hiddenBoards = s.hiddenBoards + codes, hiddenCategories = emptySet())
        }
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

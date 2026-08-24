package dev.stan.yotsuba.feature.boards

import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory

data class BoardSection(
    val category: BoardCategory,
    val boards: List<Board>,
    /** null = mixed (tri-state header). */
    val allVisible: Boolean?,
)

sealed interface BoardsUiState {
    data object Loading : BoardsUiState
    data class Error(val error: NetworkError) : BoardsUiState
    data class Success(
        val favourites: List<Board>,
        val sections: List<BoardSection>,
        val searchQuery: String,
        val editMode: Boolean,
        val hiddenBoards: Set<String>,
        val hiddenCategories: Set<String>,
        val favouriteBoardCodes: Set<String>,
    ) : BoardsUiState {
        val isEmpty: Boolean get() = favourites.isEmpty() && sections.all { it.boards.isEmpty() }
    }
}

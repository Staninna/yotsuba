package dev.stan.yotsuba.feature.boards

import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory

data class BoardSection(
    val category: BoardCategory,
    val boards: List<Board>,
    /** null = mixed (tri-state header). */
    val allVisible: Boolean?,
)

data class BoardsContent(
    val favourites: List<Board>,
    val sections: List<BoardSection>,
    val searchQuery: String,
    val editMode: Boolean,
    val hiddenBoards: Set<String>,
    val hiddenCategories: Set<String>,
    val favouriteBoardCodes: Set<String>,
) {
    val isEmpty: Boolean get() = favourites.isEmpty() && sections.all { it.boards.isEmpty() }
}

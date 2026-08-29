package dev.stan.yotsuba.feature.boards

import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory

/** One board as it appears in a section, with its per-row toggles already resolved. */
data class BoardRowState(
    val board: Board,
    val favourite: Boolean,
    val visible: Boolean,
)

data class BoardSection(
    val category: BoardCategory,
    val boards: List<BoardRowState>,
    /** null = mixed (tri-state header). */
    val allVisible: Boolean?,
)

data class BoardsContent(
    val favourites: List<Board>,
    val sections: List<BoardSection>,
    val searchQuery: String,
    val editMode: Boolean,
) {
    val isEmpty: Boolean get() = favourites.isEmpty() && sections.all { it.boards.isEmpty() }
}

package dev.stan.yotsuba.feature.boards

import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory
import dev.stan.yotsuba.domain.model.Settings

/**
 * Board visibility as one value over the two persisted sets. A board is hidden when it or
 * its whole category is; the toggles keep both sets consistent so a reader never has to
 * reconcile them. The persisted shape stays [Settings.hiddenBoards] / [Settings.hiddenCategories].
 */
data class BoardVisibility(
    val hiddenBoards: Set<String>,
    val hiddenCategories: Set<String>,
) {
    fun isVisible(board: Board): Boolean =
        board.code !in hiddenBoards && board.category.name !in hiddenCategories

    /** true = every board in [category] shown, false = none, null = mixed. */
    fun state(category: BoardCategory, all: List<Board>): Boolean? {
        val inCat = all.filter { it.category == category }
        return when (inCat.count { isVisible(it) }) {
            inCat.size -> true
            0 -> false
            else -> null
        }
    }

    fun toggleBoard(code: String, all: List<Board>): BoardVisibility {
        val category = all.firstOrNull { it.code == code }?.category
        return if (category != null && category.name in hiddenCategories) {
            // The whole category is hidden, so the user wants only this board back:
            // unhide the category and hide every sibling instead.
            val siblings = all.filter { it.category == category && it.code != code }.map { it.code }
            copy(
                hiddenCategories = hiddenCategories - category.name,
                hiddenBoards = hiddenBoards - code + siblings,
            )
        } else {
            copy(hiddenBoards = if (code in hiddenBoards) hiddenBoards - code else hiddenBoards + code)
        }
    }

    /** Mixed or all-visible -> hide all; hidden -> show all (tri-state, D13). */
    fun toggleCategory(category: BoardCategory, all: List<Board>): BoardVisibility =
        if (state(category, all) == false) {
            copy(
                hiddenCategories = hiddenCategories - category.name,
                hiddenBoards = hiddenBoards - all.filter { it.category == category }.map { it.code }.toSet(),
            )
        } else {
            copy(hiddenCategories = hiddenCategories + category.name)
        }

    fun into(settings: Settings): Settings =
        settings.copy(hiddenBoards = hiddenBoards, hiddenCategories = hiddenCategories)
}

fun Settings.visibility() = BoardVisibility(hiddenBoards, hiddenCategories)

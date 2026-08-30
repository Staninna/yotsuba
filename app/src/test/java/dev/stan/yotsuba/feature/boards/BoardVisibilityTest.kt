package dev.stan.yotsuba.feature.boards

import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory
import dev.stan.yotsuba.domain.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardVisibilityTest {

    private fun board(code: String, category: BoardCategory = BoardCategory.JAPANESE_CULTURE) = Board(
        code = code, title = code.uppercase(), description = "", worksafe = true, category = category,
        userIds = false, countryFlags = false, boardFlags = false, spoilers = false,
        webmAudio = false, codeTags = false, mathTags = false, sjisTags = false, textOnly = false,
    )

    private val jp = BoardCategory.JAPANESE_CULTURE
    private val all = listOf(board("a"), board("m"), board("c"), board("g", BoardCategory.INTERESTS))
    private val none = BoardVisibility(emptySet(), emptySet())

    @Test fun `a board is hidden by its own entry or by its category`() {
        assertTrue(none.isVisible(board("a")))
        assertFalse(BoardVisibility(setOf("a"), emptySet()).isVisible(board("a")))
        assertFalse(BoardVisibility(emptySet(), setOf(jp.name)).isVisible(board("a")))
        assertTrue(BoardVisibility(emptySet(), setOf(jp.name)).isVisible(board("g", BoardCategory.INTERESTS)))
    }

    @Test fun `category state is tri-state over its boards`() {
        assertEquals(true, none.state(jp, all))
        assertEquals(null, BoardVisibility(setOf("m"), emptySet()).state(jp, all))
        assertEquals(false, BoardVisibility(setOf("a", "m", "c"), emptySet()).state(jp, all))
        assertEquals(false, BoardVisibility(emptySet(), setOf(jp.name)).state(jp, all))
    }

    @Test fun `toggling a board flips only that board when its category is shown`() {
        val hidden = none.toggleBoard("m", all)
        assertEquals(BoardVisibility(setOf("m"), emptySet()), hidden)
        assertEquals(none, hidden.toggleBoard("m", all))
    }

    @Test fun `toggling a board under a hidden category unhides the category and hides its siblings`() {
        val result = BoardVisibility(emptySet(), setOf(jp.name)).toggleBoard("m", all)
        assertEquals(BoardVisibility(setOf("a", "c"), emptySet()), result)
    }

    @Test fun `toggling a category hides it when mixed or shown and clears everything when hidden`() {
        assertEquals(BoardVisibility(emptySet(), setOf(jp.name)), none.toggleCategory(jp, all))
        val mixed = BoardVisibility(setOf("m"), emptySet())
        assertEquals(BoardVisibility(setOf("m"), setOf(jp.name)), mixed.toggleCategory(jp, all))
        val hidden = BoardVisibility(setOf("a", "g"), setOf(jp.name))
        assertEquals(BoardVisibility(setOf("g"), emptySet()), hidden.toggleCategory(jp, all))
    }

    @Test fun `round-trips through Settings without touching other fields`() {
        val settings = Settings(favouriteBoards = setOf("g"), hiddenBoards = setOf("a"), hiddenCategories = setOf("X"))
        assertEquals(BoardVisibility(setOf("a"), setOf("X")), settings.visibility())
        val updated = BoardVisibility(setOf("b"), emptySet()).into(settings)
        assertEquals(Settings(favouriteBoards = setOf("g"), hiddenBoards = setOf("b")), updated)
    }
}

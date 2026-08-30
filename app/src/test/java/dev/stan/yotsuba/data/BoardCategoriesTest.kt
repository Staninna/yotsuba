package dev.stan.yotsuba.data

import dev.stan.yotsuba.core.network.dto.BoardsDto
import dev.stan.yotsuba.data.repository.BoardCategories
import dev.stan.yotsuba.data.repository.toDomain
import dev.stan.yotsuba.domain.model.BoardCategory
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardCategoriesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val fixture = javaClass.classLoader!!
        .getResourceAsStream("fixtures/boards.json")!!.bufferedReader().readText()

    @Test fun `every live board maps to a category`() {
        val dto = json.decodeFromString<BoardsDto>(fixture)
        // Not asserting no board falls to OTHER, since OTHER is a real category, but asserting
        // the well-known ones land where 4chan puts them.
        assertEquals(BoardCategory.INTERESTS, BoardCategories.categoryOf("g"))
        assertEquals(BoardCategory.JAPANESE_CULTURE, BoardCategories.categoryOf("jp"))
        assertEquals(BoardCategory.VIDEO_GAMES, BoardCategories.categoryOf("v"))
        assertEquals(BoardCategory.MISC, BoardCategories.categoryOf("pol"))
        assertEquals(BoardCategory.ADULT, BoardCategories.categoryOf("gif"))
        // An unknown future board falls into OTHER, never crashes.
        assertEquals(BoardCategory.OTHER, BoardCategories.categoryOf("brandnew"))
        dto.boards.forEach { BoardCategories.categoryOf(it.board) } // total function
    }

    @Test fun `capability flags survive domain mapping`() {
        val dto = json.decodeFromString<BoardsDto>(fixture)
        val g = dto.boards.first { it.board == "g" }.toDomain()
        assertTrue(g.codeTags)
        val pol = dto.boards.first { it.board == "pol" }.toDomain()
        assertTrue(pol.userIds)
        assertTrue(pol.countryFlags || pol.boardFlags)
        val sci = dto.boards.first { it.board == "sci" }.toDomain()
        assertTrue(sci.mathTags)
    }
}

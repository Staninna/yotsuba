package dev.stan.yotsuba.data

import dev.stan.yotsuba.core.network.dto.BoardsDto
import dev.stan.yotsuba.core.network.dto.CatalogPageDto
import dev.stan.yotsuba.core.network.dto.ThreadDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtoParsingTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun fixture(name: String) =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!.bufferedReader().readText()

    @Test fun `real boards json parses with capability flags`() {
        val dto = json.decodeFromString<BoardsDto>(fixture("boards.json"))
        assertEquals(77, dto.boards.size)
        assertTrue(dto.boards.any { it.board == "f" })
        assertTrue(dto.boards.first { it.board == "g" }.code_tags == 1)
        assertTrue(dto.boards.count { it.user_ids == 1 } >= 1)
        assertTrue(dto.boards.count { it.spoilers == 1 } > 10)
    }

    @Test fun `real catalog json parses`() {
        val pages = json.decodeFromString<List<CatalogPageDto>>(fixture("catalog.json"))
        assertTrue(pages.size >= 10)
        val threads = pages.flatMap { it.threads }
        assertTrue(threads.size > 100)
        assertTrue(threads.all { it.no > 0 })
    }

    @Test fun `real thread json parses, OP first`() {
        val dto = json.decodeFromString<ThreadDto>(fixture("thread.json"))
        assertTrue(dto.posts.isNotEmpty())
        assertEquals(0L, dto.posts.first().resto)
        assertNotNull(dto.posts.first().tim)
    }

    @Test fun `13-digit tim and long post numbers survive`() {
        val dto = json.decodeFromString<ThreadDto>(
            """{"posts":[{"no":9400000000,"resto":0,"time":1,"tim":1755640000123}]}"""
        )
        assertEquals(9_400_000_000L, dto.posts[0].no)
        assertEquals(1_755_640_000_123L, dto.posts[0].tim)
    }

    @Test fun `unknown fields tolerated`() {
        val dto = json.decodeFromString<ThreadDto>(
            """{"posts":[{"no":1,"time":1,"brand_new_field":"x"}]}"""
        )
        assertEquals(1L, dto.posts[0].no)
    }
}

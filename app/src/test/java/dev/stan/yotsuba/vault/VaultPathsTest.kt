package dev.stan.yotsuba.vault

import dev.stan.yotsuba.domain.model.VaultPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultPathsTest {

    @Test
    fun `sanitize replaces illegal characters and trims`() {
        assertEquals("a_b_c_d", VaultPaths.sanitizeSegment("""a/b:c?d"""))
        assertEquals("dots", VaultPaths.sanitizeSegment("dots..."))
        assertEquals("_", VaultPaths.sanitizeSegment("   "))
        assertEquals("_", VaultPaths.sanitizeSegment("???").replace(Regex("_+"), "_"))
    }

    @Test
    fun `sanitize caps segment length`() {
        assertTrue(VaultPaths.sanitizeSegment("x".repeat(300)).length <= 80)
    }

    @Test
    fun `thread dir uses subject then excerpt then bare number`() {
        assertEquals("123 - desktop thread", VaultPaths.threadDirName(123, "desktop thread"))
        assertEquals("123 - op text", VaultPaths.threadDirName(123, null, "op text"))
        assertEquals("123", VaultPaths.threadDirName(123, null, null))
        assertEquals("123", VaultPaths.threadDirName(123, "   ", ""))
    }

    @Test
    fun `file name combines post number and original name`() {
        assertEquals("456_rice.png", VaultPaths.fileName(456, "rice", ".png"))
        assertEquals("456_a_b.png", VaultPaths.fileName(456, "a/b", ".png"))
    }

    @Test
    fun `dedupe inserts counter before extension`() {
        assertEquals("a.png", VaultPaths.dedupedFileName("a.png", 0))
        assertEquals("a (1).png", VaultPaths.dedupedFileName("a.png", 1))
        assertEquals("noext (2)", VaultPaths.dedupedFileName("noext", 2))
    }

    @Test
    fun `parses cdn media urls`() {
        val parsed = VaultPaths.parseMediaUrl("https://i.4cdn.org/g/1724500000123.webm")
        assertEquals("g", parsed?.board)
        assertEquals(1724500000123L, parsed?.tim)
        assertEquals(".webm", parsed?.ext)
        assertNull(VaultPaths.parseMediaUrl("https://example.com/x.png"))
        assertNull(VaultPaths.parseMediaUrl("https://i.4cdn.org/g/1724500000123s.jpg"))
    }
}

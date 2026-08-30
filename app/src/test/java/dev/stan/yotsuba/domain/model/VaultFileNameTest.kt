package dev.stan.yotsuba.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VaultFileNameTest {

    @Test
    fun `a normal extension passes through`() {
        assertEquals("123_cat.jpg", VaultPaths.fileName(123L, "cat", ".jpg"))
        assertEquals("123_cat", VaultPaths.fileName(123L, "cat", ""))
    }

    @Test
    fun `an extension cannot introduce a path separator`() {
        val backslashed = "\\..\\x"
        for (ext in listOf("/etc/passwd", ".jpg/../../x", "..", "../", backslashed, ".jpg /x")) {
            val name = VaultPaths.fileName(1L, "a", ext)
            assertFalse("$ext produced $name", name.contains('/') || name.contains('\\'))
            assertFalse("$ext produced $name", name.endsWith(".."))
        }
    }

    @Test
    fun `thread key is board slash number`() {
        assertEquals("g/12345", threadKey("g", 12345L))
        assertEquals("g/12345", VaultLocation("g", 12345L).key)
    }
}

package dev.stan.yotsuba.data

import dev.stan.yotsuba.core.database.entity.SavedMediaEntity
import dev.stan.yotsuba.data.repository.withHashesFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A rescan rebuilds rows from sidecars that never held a hash; the old rows must lend theirs. */
class MediaVaultRescanHashesTest {

    private fun row(url: String, path: String, md5: String? = null, phash: Long? = null, pixels: Long? = null) =
        SavedMediaEntity(
            url = url, board = "g", threadNo = 1, postNo = 2, subject = null, displayName = "f.jpg",
            absolutePath = path, ext = ".jpg", sizeBytes = 1, width = 1, height = 1, thumbnailUrl = null,
            savedAt = 0, md5 = md5, phash = phash, pixelSize = pixels,
        )

    @Test fun `hashes follow the path`() {
        val old = listOf(row("u1", "/v/g/1/f.jpg", md5 = "m", phash = 7, pixels = 9))
        val rebuilt = listOf(row("u1", "/v/g/1/f.jpg")).withHashesFrom(old).single()
        assertEquals("m", rebuilt.md5)
        assertEquals(7L, rebuilt.phash)
        assertEquals(9L, rebuilt.pixelSize)
    }

    @Test fun `a moved file is found by its url`() {
        val old = listOf(row("u1", "/v/g/1/f.jpg", md5 = "m", phash = 7))
        val rebuilt = listOf(row("u1", "/v/g/2/f.jpg")).withHashesFrom(old).single()
        assertEquals("m", rebuilt.md5)
        assertEquals(7L, rebuilt.phash)
    }

    @Test fun `a new file keeps no hash`() {
        val old = listOf(row("u1", "/v/g/1/f.jpg", md5 = "m"))
        val rebuilt = listOf(row("u2", "/v/g/1/g.jpg")).withHashesFrom(old).single()
        assertNull(rebuilt.md5)
    }

    @Test fun `path wins over url when they disagree`() {
        val old = listOf(row("u1", "/v/g/1/f.jpg", md5 = "by-path"), row("u2", "/v/g/1/x.jpg", md5 = "by-url"))
        val rebuilt = listOf(row("u2", "/v/g/1/f.jpg")).withHashesFrom(old).single()
        assertEquals("by-path", rebuilt.md5)
    }
}

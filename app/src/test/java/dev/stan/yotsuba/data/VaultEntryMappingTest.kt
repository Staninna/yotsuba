package dev.stan.yotsuba.data

import dev.stan.yotsuba.core.database.entity.SavedMediaEntity
import dev.stan.yotsuba.data.repository.toVaultEntry
import dev.stan.yotsuba.domain.model.VaultLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultEntryMappingTest {

    private fun entity(
        board: String? = "g",
        threadNo: Long? = 100L,
        subject: String? = "subj",
        ext: String? = ".jpg",
    ) = SavedMediaEntity(
        url = "https://i.4cdn.org/g/123.jpg",
        board = board,
        threadNo = threadNo,
        postNo = 5L,
        subject = subject,
        displayName = "123.jpg",
        absolutePath = "/sdcard/Yotsuba/g/t/123.jpg",
        ext = ext,
        sizeBytes = 10L,
        width = 640,
        height = 480,
        thumbnailUrl = "thumb",
        savedAt = 1L,
    )

    @Test
    fun `thread rows map to a thread location`() {
        val entry = entity().toVaultEntry()
        assertEquals(VaultLocation("g", 100L), entry.location)
        assertEquals("subj", entry.subject)
        assertEquals("123.jpg", entry.displayName)
    }

    @Test
    fun `rows without a thread are unsorted`() {
        assertEquals(VaultLocation.Unsorted, entity(board = null, threadNo = null).toVaultEntry().location)
        assertEquals(VaultLocation.Unsorted, entity(threadNo = null).toVaultEntry().location)
        assertEquals(VaultLocation.Unsorted, entity(board = "_unsorted").toVaultEntry().location)
    }

    @Test
    fun `video detection follows the extension`() {
        assertTrue(entity(ext = ".webm").toVaultEntry().isVideo)
        assertTrue(entity(ext = ".mp4").toVaultEntry().isVideo)
        assertFalse(entity(ext = ".jpg").toVaultEntry().isVideo)
        assertFalse(entity(ext = null).toVaultEntry().isVideo)
    }
}

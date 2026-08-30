package dev.stan.yotsuba.data

import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.core.vault.VaultThreadMeta
import dev.stan.yotsuba.data.repository.savedMediaEntity
import dev.stan.yotsuba.data.repository.toVaultEntry
import dev.stan.yotsuba.data.repository.unsortedSavedMediaEntity
import dev.stan.yotsuba.data.repository.urlOnlySavedMediaEntity
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.VaultLocation
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedMediaMappersTest {

    private val item = MediaItem(
        postNo = 123, filename = "cat", ext = ".jpg", sizeBytes = 999, width = 100, height = 200,
        thumbnailUrl = "https://i.4cdn.org/g/1700000000000s.jpg",
        fullUrl = "https://i.4cdn.org/g/1700000000000.jpg", spoiler = false,
    )

    @Test fun `fresh save is filed under its thread`() {
        val target = File("/vault/g/42-subject/cat.jpg")
        val entity = savedMediaEntity(item, board = "g", threadNo = 42, subject = "subject", target = target, savedAt = 555)
        assertEquals(item.fullUrl, entity.url)
        assertEquals("g", entity.board)
        assertEquals(42L, entity.threadNo)
        assertEquals(123L, entity.postNo)
        assertEquals("subject", entity.subject)
        assertEquals("cat.jpg", entity.displayName)
        assertEquals(target.absolutePath, entity.absolutePath)
        assertEquals(".jpg", entity.ext)
        assertEquals(999L, entity.sizeBytes)
        assertEquals(555L, entity.savedAt)
    }

    @Test fun `rescan row from meta sidecar uses meta fields`() {
        val meta = VaultThreadMeta(board = "g", threadNo = 42, subject = "s")
        val f = VaultFileMeta(
            fileName = "cat.jpg", postNo = 1, url = "https://i.4cdn.org/g/1.jpg",
            ext = ".jpg", sizeBytes = 10, savedAtMillis = 777,
        )
        val file = File("/vault/g/42/cat.jpg")
        val entity = savedMediaEntity(meta, f, file)
        assertEquals("https://i.4cdn.org/g/1.jpg", entity.url)
        assertEquals("g", entity.board)
        assertEquals(42L, entity.threadNo)
        assertEquals(10L, entity.sizeBytes)
        assertEquals(777L, entity.savedAt)
        assertEquals("cat.jpg", entity.displayName)
    }

    @Test fun `fresh save carries the probe and the sound-post url both ways`() {
        val sound = item.copy(ext = ".webm", soundUrl = "https://files.catbox.moe/a.mp3")
        val entity = savedMediaEntity(
            sound, board = "g", threadNo = 42, subject = null, target = File("/vault/g/42/cat.webm"), savedAt = 1,
            durationMs = 1500, hasAudio = false,
        )
        assertEquals(false, entity.hasAudio)
        assertEquals("https://files.catbox.moe/a.mp3", entity.soundUrl)
        val vaultEntry = entity.toVaultEntry()
        assertEquals(false, vaultEntry.hasAudio)
        assertEquals("https://files.catbox.moe/a.mp3", vaultEntry.soundUrl)
        assertEquals(true, vaultEntry.hasSound)
    }

    @Test fun `rescan row keeps hasAudio from the sidecar, null when it was never probed`() {
        val meta = VaultThreadMeta(board = "g", threadNo = 42)
        val probed = VaultFileMeta(fileName = "a.webm", ext = ".webm", url = "https://i.4cdn.org/g/1.webm", hasAudio = true)
        val old = VaultFileMeta(fileName = "b.webm", ext = ".webm", url = "https://i.4cdn.org/g/2.webm")
        assertEquals(true, savedMediaEntity(meta, probed, File("/vault/g/42/a.webm")).toVaultEntry().hasAudio)
        val unprobed = savedMediaEntity(meta, old, File("/vault/g/42/b.webm")).toVaultEntry()
        assertNull(unprobed.hasAudio)
        assertEquals(false, unprobed.hasSound)
    }

    @Test fun `rescan row without url is keyed by file path`() {
        val meta = VaultThreadMeta(board = "_unsorted")
        val file = File("/vault/_unsorted/x.png")
        val entity = savedMediaEntity(meta, VaultFileMeta(fileName = "x.png"), file)
        assertEquals("file:///vault/_unsorted/x.png", entity.url)
    }

    @Test fun `unsorted migration entity`() {
        val target = File("/vault/_unsorted/y.webm")
        val entity = unsortedSavedMediaEntity(target, savedAt = 9)
        assertEquals("file:///vault/_unsorted/y.webm", entity.url)
        assertNull(entity.board)
        assertNull(entity.threadNo)
        assertEquals("y.webm", entity.displayName)
        assertEquals(".webm", entity.ext)
        assertEquals(9L, entity.savedAt)
    }

    @Test fun `url-only legacy entity parses board and ext from url`() {
        val entity = urlOnlySavedMediaEntity("https://i.4cdn.org/g/1700000000000.webm", downloadedAt = 5)
        assertEquals("g", entity.board)
        assertEquals(".webm", entity.ext)
        assertEquals("1700000000000.webm", entity.displayName)
        assertEquals("", entity.absolutePath)
        assertEquals(5L, entity.savedAt)
    }

    @Test fun `toVaultEntry files thread rows under Thread`() {
        val entity = savedMediaEntity(item, "g", 42, "s", File("/vault/g/42/cat.jpg"), 1)
        val entry = entity.toVaultEntry()
        assertEquals(VaultLocation("g", 42), entry.location)
        assertEquals("s", entry.subject)
        assertEquals("cat.jpg", entry.displayName)
        assertEquals(1L, entry.savedAt)
    }

    @Test fun `toVaultEntry files unsorted and _unsorted-board rows under Unsorted`() {
        val unsorted = unsortedSavedMediaEntity(File("/vault/_unsorted/y.png"), 1).toVaultEntry()
        assertEquals(VaultLocation.Unsorted, unsorted.location)

        val meta = VaultThreadMeta(board = "_unsorted", threadNo = 1)
        val row = savedMediaEntity(meta, VaultFileMeta(fileName = "z.png"), File("/vault/_unsorted/z.png"))
        assertEquals(VaultLocation.Unsorted, row.toVaultEntry().location)
    }
}

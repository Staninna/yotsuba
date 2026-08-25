package dev.stan.yotsuba.vault

import dev.stan.yotsuba.core.text.PostText
import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.core.vault.VaultMetaCodec
import dev.stan.yotsuba.core.vault.VaultPaths
import dev.stan.yotsuba.data.repository.VaultStore
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.ThreadPost
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VaultStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private val store = VaultStore()

    private fun mediaItem() = MediaItem(
        postNo = 123,
        filename = "cat",
        ext = ".jpg",
        sizeBytes = 42_000,
        width = 800,
        height = 600,
        thumbnailUrl = "https://i.4cdn.org/g/1700000000000s.jpg",
        fullUrl = "https://i.4cdn.org/g/1700000000000.jpg",
        spoiler = true,
    )

    private fun threadPost(item: MediaItem) = ThreadPost(
        board = "g",
        no = 123,
        isOp = false,
        name = "Anonymous",
        tripcode = "!trip",
        capcode = null,
        posterId = null,
        countryCode = null,
        countryName = null,
        timeSeconds = 1_700_000_000,
        subject = null,
        body = PostText(emptyList()),
        media = PostMedia.Present(item),
        quotedPostNos = emptyList(),
    )

    @Test
    fun `uniqueFile returns unused deduped name`() {
        val dir = tmp.newFolder()
        assertEquals("a.jpg", store.uniqueFile(dir, "a.jpg").name)
        File(dir, "a.jpg").createNewFile()
        val second = store.uniqueFile(dir, "a.jpg")
        assertEquals(VaultPaths.dedupedFileName("a.jpg", 1), second.name)
        second.createNewFile()
        assertEquals(VaultPaths.dedupedFileName("a.jpg", 2), store.uniqueFile(dir, "a.jpg").name)
    }

    @Test
    fun `moveFile moves content and removes source`() {
        val from = tmp.newFile().apply { writeText("payload") }
        val to = File(tmp.newFolder(), "moved.bin")
        assertTrue(store.moveFile(from, to))
        assertEquals("payload", to.readText())
        assertFalse(from.exists())
    }

    @Test
    fun `moveFile replaces existing target via rename`() {
        // POSIX rename overwrites; only the copy fallback refuses to.
        val from = tmp.newFile().apply { writeText("new") }
        val to = tmp.newFile().apply { writeText("old") }
        assertTrue(store.moveFile(from, to))
        assertEquals("new", to.readText())
        assertFalse(from.exists())
    }

    @Test
    fun `updateMeta creates then rewrites meta json`() {
        val board = File(tmp.root, "g").apply { mkdirs() }
        val dir = File(board, "123-thread").apply { mkdirs() }
        store.updateMeta(dir) { it.copy(threadNo = 123) }
        store.updateMeta(dir) { it.upsert(VaultFileMeta(fileName = "a.jpg")) }
        val meta = VaultMetaCodec.decode(File(dir, VaultPaths.META_FILE_NAME).readText())!!
        assertEquals("g", meta.board)
        assertEquals(123L, meta.threadNo)
        assertEquals(listOf("a.jpg"), meta.files.map { it.fileName })
    }

    @Test
    fun `pruneIfEmpty removes dir with only empty meta`() {
        val board = File(tmp.root, "g").apply { mkdirs() }
        val dir = File(board, "123-thread").apply { mkdirs() }
        store.updateMeta(dir) { it }
        store.pruneIfEmpty(dir)
        assertFalse(dir.exists())
    }

    @Test
    fun `pruneIfEmpty keeps dir holding files or meta entries`() {
        val board = File(tmp.root, "g").apply { mkdirs() }
        val dir = File(board, "123-thread").apply { mkdirs() }
        store.updateMeta(dir) { it.upsert(VaultFileMeta(fileName = "a.jpg")) }
        store.pruneIfEmpty(dir)
        assertTrue(dir.exists())
    }

    @Test
    fun `fileMetaOf copies item and post fields`() {
        val item = mediaItem()
        val meta = store.fileMetaOf("123_cat.jpg", item, threadPost(item), savedAt = 999L)
        assertEquals("123_cat.jpg", meta.fileName)
        assertEquals(123L, meta.postNo)
        assertEquals(1_700_000_000_000L, meta.tim)
        assertEquals("cat", meta.originalFilename)
        assertEquals(item.fullUrl, meta.url)
        assertEquals(item.thumbnailUrl, meta.thumbnailUrl)
        assertEquals(800, meta.width!!.toInt())
        assertEquals(42_000L, meta.sizeBytes)
        assertTrue(meta.spoiler)
        assertEquals("Anonymous", meta.posterName)
        assertEquals("!trip", meta.tripcode)
        assertEquals(1_700_000_000L, meta.postedAtSeconds)
        assertNull(meta.postText)
        assertEquals(999L, meta.savedAtMillis)
    }

    @Test
    fun `fileMetaOf without post leaves poster fields null`() {
        val meta = store.fileMetaOf("f.jpg", mediaItem(), post = null, savedAt = 1L)
        assertNull(meta.posterName)
        assertNull(meta.tripcode)
        assertNull(meta.postedAtSeconds)
    }
}

package dev.stan.yotsuba.vault

import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.core.vault.VaultPostMeta
import dev.stan.yotsuba.data.repository.VaultStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Sidecar compaction on a dead thread, against a temp root standing in for /sdcard/Yotsuba. */
class VaultPruneTest {

    @get:Rule val tmp = TemporaryFolder()

    private val store by lazy { VaultStore(tmp.root) }

    /** A saved thread directory: posts.json plus a meta.json naming the thread. */
    private fun savedDir(posts: List<VaultPostMeta>): File {
        val dir = File(File(tmp.root, "g"), "1 - Cats").apply { mkdirs() }
        store.updatePosts(dir, "g", 1, posts)
        store.updateMeta(dir) { it.copy(threadNo = 1, subject = "Cats") }
        return dir
    }

    @Test
    fun `pruneDeadThread keeps OP plus the conversation around each saved file`() {
        val dir = savedDir(vaultThread())
        File(dir, "4_pic.jpg").writeText("bytes")
        store.updateMeta(dir) { it.upsert(VaultFileMeta(fileName = "4_pic.jpg", postNo = 4)) }

        assertEquals(3, store.pruneDeadThread(dir))
        assertEquals(listOf(1L, 4L, 5L), store.readPosts(dir)!!.posts.map { it.no })
        val meta = readMeta(dir)
        assertNotNull(meta.prunedAt)
        assertEquals(3, meta.prunedPostCount)
        assertTrue(meta.isPruned)
    }

    @Test
    fun `pruneDeadThread follows quotes both ways`() {
        val dir = savedDir(vaultThread())
        File(dir, "3_pic.jpg").writeText("bytes")
        store.updateMeta(dir) { it.upsert(VaultFileMeta(fileName = "3_pic.jpg", postNo = 3)) }

        store.pruneDeadThread(dir)
        // 3 quotes 2 quotes 1; 6 replies to 3. 4 and 5 are another conversation.
        assertEquals(listOf(1L, 2L, 3L, 6L), store.readPosts(dir)!!.posts.map { it.no })
    }

    @Test
    fun `a snapshot-only thread is never pruned`() {
        val dir = savedDir(vaultThread())
        assertNull(store.pruneDeadThread(dir))
        assertEquals(6, store.readPosts(dir)!!.posts.size)
        assertNull(readMeta(dir).prunedAt)
    }

    @Test
    fun `a sidecar entry whose file is missing does not count as saved`() {
        val dir = savedDir(vaultThread())
        store.updateMeta(dir) { it.upsert(VaultFileMeta(fileName = "gone.jpg", postNo = 4)) }
        assertNull(store.pruneDeadThread(dir))
    }

    @Test
    fun `pruning is idempotent`() {
        val dir = savedDir(vaultThread())
        File(dir, "4_pic.jpg").writeText("bytes")
        store.updateMeta(dir) { it.upsert(VaultFileMeta(fileName = "4_pic.jpg", postNo = 4)) }
        assertEquals(3, store.pruneDeadThread(dir))
        val first = readMeta(dir)

        assertNull(store.pruneDeadThread(dir))
        assertEquals(first, readMeta(dir))
        assertEquals(listOf(1L, 4L, 5L), store.readPosts(dir)!!.posts.map { it.no })
    }
}

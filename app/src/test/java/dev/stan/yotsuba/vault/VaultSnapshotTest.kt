package dev.stan.yotsuba.vault

import dev.stan.yotsuba.core.text.PostSegment
import dev.stan.yotsuba.core.text.PostText
import dev.stan.yotsuba.core.vault.VaultFileMeta
import dev.stan.yotsuba.core.vault.VaultMetaCodec
import dev.stan.yotsuba.core.vault.VaultPaths
import dev.stan.yotsuba.core.vault.VaultPostMeta
import dev.stan.yotsuba.core.vault.toThreadPost
import dev.stan.yotsuba.data.repository.VaultStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Snapshot and prune at the sidecar level, against a temp root standing in for /sdcard/Yotsuba. */
class VaultSnapshotTest {

    @get:Rule val tmp = TemporaryFolder()

    private val store by lazy { VaultStore(tmp.root) }

    private fun post(no: Long, quotes: List<Long> = emptyList(), isOp: Boolean = false) = VaultPostMeta(
        no = no,
        isOp = isOp,
        subject = if (isOp) "Cats" else null,
        timeSeconds = 1_700_000_000 + no,
        body = PostText(listOf(PostSegment("post $no"))),
        quotedPostNos = quotes,
    )

    /** OP 1; 2 -> 1; 3 -> 2; 4 stray; 5 -> 4; 6 -> 3. */
    private fun thread() = listOf(
        post(1, isOp = true), post(2, listOf(1)), post(3, listOf(2)),
        post(4), post(5, listOf(4)), post(6, listOf(3)),
    )

    private fun meta(dir: File) = VaultMetaCodec.decode(File(dir, VaultPaths.META_FILE_NAME).readText())!!

    @Test
    fun `snapshot creates a sidecar-only directory the index and savedThread can read`() {
        val dir = store.snapshot("g", 1, "Cats", null, thread())!!
        assertEquals("1 - Cats", dir.name)
        assertEquals("g", dir.parentFile!!.name)

        val stored = store.threadMetas().single()
        assertEquals(dir, stored.dir)
        assertEquals(1L, stored.meta.threadNo)
        assertTrue(stored.meta.files.isEmpty())
        assertNotNull(stored.meta.snapshotAt)

        // What savedThread() rebuilds from: found by number, decoded as ThreadPosts.
        val saved = store.readPosts(store.threadDir("g", 1)!!)!!
        assertEquals((1L..6L).toList(), saved.posts.map { it.no })
        val op = saved.posts.first().toThreadPost("g")
        assertTrue(op.isOp)
        assertEquals("Cats", op.subject)
    }

    @Test
    fun `snapshot merges into an existing directory and survives pruneIfEmpty`() {
        val dir = store.snapshot("g", 1, "Cats", null, thread().take(3))!!
        store.snapshot("g", 1, "Cats", null, thread())
        assertEquals(6, store.readPosts(dir)!!.posts.size)
        assertEquals(1, store.threadMetas().size)

        store.pruneIfEmpty(dir)
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `snapshot refuses a pruned thread`() {
        val dir = store.snapshot("g", 1, "Cats", null, thread())!!
        File(dir, "4_pic.jpg").writeText("bytes")
        store.updateMeta(dir) { it.upsert(VaultFileMeta(fileName = "4_pic.jpg", postNo = 4)) }
        assertNotNull(store.pruneDeadThread(dir))

        assertNull(store.snapshot("g", 1, "Cats", null, thread() + post(7)))
        assertEquals(listOf(1L, 4L, 5L), store.readPosts(dir)!!.posts.map { it.no })
    }
}

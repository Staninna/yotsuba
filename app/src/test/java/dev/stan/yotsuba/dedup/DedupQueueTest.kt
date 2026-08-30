package dev.stan.yotsuba.dedup

import dev.stan.yotsuba.data.repository.MediaDownloadQueue
import dev.stan.yotsuba.domain.model.DedupMode
import dev.stan.yotsuba.domain.model.DuplicateGroup
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.fake.FakeMediaVault
import dev.stan.yotsuba.domain.repository.VaultDedupRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DedupQueueTest {
    private fun item(name: String, md5: String?) = MediaItem(
        postNo = 1, filename = name, ext = ".jpg", sizeBytes = 1, width = 1, height = 1,
        thumbnailUrl = "https://cdn/thumb/$name.jpg", fullUrl = "https://cdn/$name.jpg",
        spoiler = false, md5 = md5,
    )

    private val ctx = VaultSaveContext(board = "g", threadNo = 100, threadSubject = null, opExcerpt = null, post = null)

    private class RecordingVault : FakeMediaVault() {
        val saved = mutableListOf<String>()
        override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? { saved += item.fullUrl; return null }
    }

    private class KnownMd5s(private val known: Map<String, String>) : VaultDedupRepository {
        val recorded = mutableListOf<Pair<String, String>>()
        override suspend fun findByMd5(md5: String): String? = known[md5]
        override suspend fun recordMd5(url: String, md5: String) { recorded += url to md5 }
        override suspend fun missingHashCount() = 0
        override suspend fun backfillHashes(onProgress: (Int, Int) -> Unit) {}
        override suspend fun findDuplicates(mode: DedupMode, maxDistance: Int): List<DuplicateGroup> = emptyList()
    }

    private fun TestScope.queue(vault: FakeMediaVault, dedup: VaultDedupRepository) =
        MediaDownloadQueue(vault, dedup, backgroundScope, StandardTestDispatcher(testScheduler))

    private suspend fun MediaDownloadQueue.status(url: String) = statuses.first()[url]

    @Test fun `a known md5 reads AlreadySaved and is never downloaded`() = runTest {
        val vault = RecordingVault()
        val dedup = KnownMd5s(mapOf("abc=" to "/vault/g/1 - t/a.jpg"))
        val queue = queue(vault, dedup)
        val a = item("a", md5 = "abc=")
        queue.enqueue(a, ctx)
        runCurrent()
        assertEquals(MediaSaveStatus.AlreadySaved("/vault/g/1 - t/a.jpg"), queue.status(a.fullUrl))
        assertTrue(vault.saved.isEmpty())
        queue.dismiss(a.fullUrl)
        assertNull(queue.status(a.fullUrl))
    }

    @Test fun `an unknown md5 downloads and is recorded afterwards`() = runTest {
        val vault = RecordingVault()
        val dedup = KnownMd5s(emptyMap())
        val queue = queue(vault, dedup)
        val a = item("a", md5 = "new=")
        queue.enqueue(a, ctx)
        runCurrent()
        assertNull(queue.status(a.fullUrl))
        assertEquals(listOf(a.fullUrl), vault.saved)
        assertEquals(listOf(a.fullUrl to "new="), dedup.recorded)
    }

    @Test fun `no md5 means no lookup and a plain download`() = runTest {
        val vault = RecordingVault()
        val queue = queue(vault, KnownMd5s(mapOf("x" to "/y")))
        val a = item("a", md5 = null)
        queue.enqueue(a, ctx)
        runCurrent()
        assertNull(queue.status(a.fullUrl))
        assertEquals(listOf(a.fullUrl), vault.saved)
    }
}

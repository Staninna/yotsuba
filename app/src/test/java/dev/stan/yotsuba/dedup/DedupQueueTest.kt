package dev.stan.yotsuba.dedup

import dev.stan.yotsuba.data.repository.MediaDownloadQueue
import dev.stan.yotsuba.domain.model.DedupMode
import dev.stan.yotsuba.domain.model.DuplicateGroup
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.VaultDedupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupQueueTest {
    private fun item(name: String, md5: String?) = MediaItem(
        postNo = 1, filename = name, ext = ".jpg", sizeBytes = 1, width = 1, height = 1,
        thumbnailUrl = "https://cdn/thumb/$name.jpg", fullUrl = "https://cdn/$name.jpg",
        spoiler = false, md5 = md5,
    )

    private val ctx = VaultSaveContext(board = "g", threadNo = 100, threadSubject = null, opExcerpt = null, post = null)

    private class RecordingVault : MediaVaultRepository {
        val saved = mutableListOf<String>()
        override fun hasStorageAccess() = true
        override fun entries(): Flow<List<VaultEntry>> = flowOf(emptyList())
        override fun saved(): Flow<Map<String, String?>> = flowOf(emptyMap())
        override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? { saved += item.fullUrl; return null }
        override suspend fun delete(url: String): VaultError? = null
        override suspend fun syncSavedThreads(onProgress: (Int, Int) -> Unit) = VaultSyncSummary()
        override suspend fun importLocalThread(name: String, sources: List<ImportSource>): VaultError? = null
        override suspend fun savedThread(board: String, threadNo: Long): ThreadDetails? = null
        override suspend fun rescan() {}
        override suspend fun migrateLegacyIfNeeded() {}
    }

    private class KnownMd5s(private val known: Map<String, String>) : VaultDedupRepository {
        val recorded = mutableListOf<Pair<String, String>>()
        override suspend fun findByMd5(md5: String): String? = known[md5]
        override suspend fun recordMd5(url: String, md5: String) { recorded += url to md5 }
        override suspend fun missingHashCount() = 0
        override suspend fun backfillHashes(onProgress: (Int, Int) -> Unit) {}
        override suspend fun findDuplicates(mode: DedupMode, maxDistance: Int): List<DuplicateGroup> = emptyList()
    }

    private suspend fun MediaDownloadQueue.await(url: String, check: (MediaSaveStatus?) -> Boolean) =
        withTimeout(5_000) { statuses.first { check(it[url]) } }

    @Test fun `a known md5 reads AlreadySaved and is never downloaded`() = runBlocking<Unit> {
        val vault = RecordingVault()
        val dedup = KnownMd5s(mapOf("abc=" to "/vault/g/1 - t/a.jpg"))
        val queue = MediaDownloadQueue(vault, dedup)
        val a = item("a", md5 = "abc=")
        queue.enqueue(a, ctx)
        val statuses = queue.await(a.fullUrl) { it is MediaSaveStatus.AlreadySaved }
        assertEquals(MediaSaveStatus.AlreadySaved("/vault/g/1 - t/a.jpg"), statuses[a.fullUrl])
        assertTrue(vault.saved.isEmpty())
        queue.dismiss(a.fullUrl)
        queue.await(a.fullUrl) { it == null }
    }

    @Test fun `an unknown md5 downloads and is recorded afterwards`() = runBlocking<Unit> {
        val vault = RecordingVault()
        val dedup = KnownMd5s(emptyMap())
        val queue = MediaDownloadQueue(vault, dedup)
        val a = item("a", md5 = "new=")
        queue.enqueue(a, ctx)
        queue.await(a.fullUrl) { it == null }
        assertEquals(listOf(a.fullUrl), vault.saved)
        assertEquals(listOf(a.fullUrl to "new="), dedup.recorded)
    }

    @Test fun `no md5 means no lookup and a plain download`() = runBlocking<Unit> {
        val vault = RecordingVault()
        val queue = MediaDownloadQueue(vault, KnownMd5s(mapOf("x" to "/y")))
        val a = item("a", md5 = null)
        queue.enqueue(a, ctx)
        queue.await(a.fullUrl) { it == null }
        assertEquals(listOf(a.fullUrl), vault.saved)
    }
}

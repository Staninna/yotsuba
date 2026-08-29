package dev.stan.yotsuba.data

import dev.stan.yotsuba.data.repository.DownloadState
import dev.stan.yotsuba.data.repository.MediaDownloadQueue
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The queue owns a real IO-dispatcher worker (its scope is not injectable), so these tests
 * run against real time: the fake vault gates each save on a [CompletableDeferred] and the
 * assertions await the resulting status transitions with a timeout.
 */
class MediaDownloadQueueTest {

    private fun item(name: String) = MediaItem(
        postNo = 1, filename = name, ext = ".jpg", sizeBytes = 1, width = 1, height = 1,
        thumbnailUrl = "https://cdn/thumb/$name.jpg", fullUrl = "https://cdn/$name.jpg",
        spoiler = false,
    )

    private val ctx = VaultSaveContext(board = "g", threadNo = 100, threadSubject = null, opExcerpt = null, post = null)

    /** Each save suspends until the test releases its gate, then returns the scripted result. */
    private class GatedVault : MediaVaultRepository {
        val gates = MutableStateFlow<Map<String, CompletableDeferred<VaultError?>>>(emptyMap())
        private val lock = Mutex()
        val saveCalls = mutableListOf<String>()

        suspend fun awaitSaveStarted(url: String): CompletableDeferred<VaultError?> =
            withTimeout(5_000) { gates.first { url in it }.getValue(url) }

        override fun hasStorageAccess() = true
        override fun entries(): Flow<List<VaultEntry>> = flowOf(emptyList())
        override fun savedUrls(): Flow<Set<String>> = flowOf(emptySet())
        override fun savedPaths(): Flow<Map<String, String>> = flowOf(emptyMap())
        override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? {
            val gate = CompletableDeferred<VaultError?>()
            lock.withLock { saveCalls += item.fullUrl }
            gates.value = gates.value + (item.fullUrl to gate)
            return gate.await()
        }
        override suspend fun delete(url: String): VaultError? = null
        override suspend fun savedThread(board: String, threadNo: Long): ThreadDetails? = null
        override suspend fun rescan() {}
        override suspend fun migrateLegacyIfNeeded() {}
    }

    private suspend fun MediaDownloadQueue.awaitStatus(url: String, expected: DownloadState?) =
        withTimeout(5_000) { statuses.first { it[url] == expected } }

    @Test fun `enqueue walks queued to downloading and disappears on success`() = runBlocking<Unit> {
        val vault = GatedVault()
        val queue = MediaDownloadQueue(vault)
        val a = item("a")
        queue.enqueue(a, ctx)
        queue.awaitStatus(a.fullUrl, DownloadState.Downloading)
        vault.awaitSaveStarted(a.fullUrl).complete(null)
        queue.awaitStatus(a.fullUrl, null) // success: entry vanishes, saved table takes over
        assertEquals(listOf(a.fullUrl), vault.saveCalls)
    }

    @Test fun `a failed save keeps a failed marker with the error`() = runBlocking<Unit> {
        val vault = GatedVault()
        val queue = MediaDownloadQueue(vault)
        val a = item("a")
        queue.enqueue(a, ctx)
        vault.awaitSaveStarted(a.fullUrl).complete(VaultError.NoAccess)
        val statuses = queue.awaitStatus(a.fullUrl, DownloadState.Failed(VaultError.NoAccess))
        assertEquals(DownloadState.Failed(VaultError.NoAccess), statuses[a.fullUrl])
    }

    @Test fun `second item waits queued behind the one downloading`() = runBlocking<Unit> {
        val vault = GatedVault()
        val queue = MediaDownloadQueue(vault)
        val a = item("a")
        val b = item("b")
        queue.enqueue(a, ctx)
        vault.awaitSaveStarted(a.fullUrl)
        queue.enqueue(b, ctx)
        assertEquals(DownloadState.Queued, queue.statuses.value[b.fullUrl])
        vault.awaitSaveStarted(a.fullUrl).complete(null)
        queue.awaitStatus(b.fullUrl, DownloadState.Downloading)
        vault.awaitSaveStarted(b.fullUrl).complete(null)
        queue.awaitStatus(b.fullUrl, null)
        assertEquals(listOf(a.fullUrl, b.fullUrl), vault.saveCalls)
    }

    @Test fun `enqueueing the same url twice downloads it once`() = runBlocking<Unit> {
        val vault = GatedVault()
        val queue = MediaDownloadQueue(vault)
        val blocker = item("blocker")
        val a = item("a")
        queue.enqueue(blocker, ctx)
        vault.awaitSaveStarted(blocker.fullUrl)
        queue.enqueue(a, ctx)
        queue.enqueue(a, ctx) // duplicate while queued: no-op
        vault.awaitSaveStarted(blocker.fullUrl).complete(null)
        queue.awaitStatus(a.fullUrl, DownloadState.Downloading)
        queue.enqueue(a, ctx) // duplicate while downloading: no-op
        vault.awaitSaveStarted(a.fullUrl).complete(null)
        queue.awaitStatus(a.fullUrl, null)
        assertEquals(listOf(blocker.fullUrl, a.fullUrl), vault.saveCalls)
    }

    @Test fun `cancel removes a queued entry and the worker skips it`() = runBlocking<Unit> {
        val vault = GatedVault()
        val queue = MediaDownloadQueue(vault)
        val a = item("a")
        val b = item("b")
        val c = item("c")
        queue.enqueue(a, ctx)
        vault.awaitSaveStarted(a.fullUrl)
        queue.enqueue(b, ctx)
        queue.cancel(b.fullUrl)
        assertNull(queue.statuses.value[b.fullUrl])
        queue.enqueue(c, ctx)
        vault.awaitSaveStarted(a.fullUrl).complete(null)
        vault.awaitSaveStarted(c.fullUrl).complete(null)
        queue.awaitStatus(c.fullUrl, null)
        assertEquals(listOf(a.fullUrl, c.fullUrl), vault.saveCalls)
    }

    @Test fun `cancel does not touch an entry already downloading`() = runBlocking<Unit> {
        val vault = GatedVault()
        val queue = MediaDownloadQueue(vault)
        val a = item("a")
        queue.enqueue(a, ctx)
        queue.awaitStatus(a.fullUrl, DownloadState.Downloading)
        queue.cancel(a.fullUrl)
        assertEquals(DownloadState.Downloading, queue.statuses.value[a.fullUrl])
        vault.awaitSaveStarted(a.fullUrl).complete(null)
        queue.awaitStatus(a.fullUrl, null)
    }

    @Test fun `dismiss failed clears the marker without retrying`() = runBlocking<Unit> {
        val vault = GatedVault()
        val queue = MediaDownloadQueue(vault)
        val a = item("a")
        queue.enqueue(a, ctx)
        vault.awaitSaveStarted(a.fullUrl).complete(VaultError.Io("boom"))
        queue.awaitStatus(a.fullUrl, DownloadState.Failed(VaultError.Io("boom")))
        queue.dismissFailed(a.fullUrl)
        assertNull(queue.statuses.value[a.fullUrl])
        assertEquals(listOf(a.fullUrl), vault.saveCalls)
    }

    @Test fun `a failed entry can be re-enqueued to retry`() = runBlocking<Unit> {
        val vault = GatedVault()
        val queue = MediaDownloadQueue(vault)
        val a = item("a")
        queue.enqueue(a, ctx)
        vault.awaitSaveStarted(a.fullUrl).complete(VaultError.Io("boom"))
        queue.awaitStatus(a.fullUrl, DownloadState.Failed(VaultError.Io("boom")))
        // Reset the gate map so the retry's save registers a fresh gate.
        vault.gates.value = emptyMap()
        queue.enqueue(a, ctx)
        vault.awaitSaveStarted(a.fullUrl).complete(null)
        queue.awaitStatus(a.fullUrl, null)
        assertEquals(listOf(a.fullUrl, a.fullUrl), vault.saveCalls)
        assertTrue(queue.statuses.value.isEmpty())
    }
}

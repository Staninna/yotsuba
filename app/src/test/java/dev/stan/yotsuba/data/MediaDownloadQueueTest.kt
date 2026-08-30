package dev.stan.yotsuba.data

import dev.stan.yotsuba.data.repository.MediaDownloadQueue
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.fake.FakeMediaVault
import dev.stan.yotsuba.fake.NoDedup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The worker runs on the test scheduler: the fake vault suspends each save until the test
 * hands it a result, and `runCurrent` drains the worker (it lives in `backgroundScope`, which
 * `advanceUntilIdle` alone does not drive) to its next resting point.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaDownloadQueueTest {

    private fun item(name: String) = MediaItem(
        postNo = 1, filename = name, ext = ".jpg", sizeBytes = 1, width = 1, height = 1,
        thumbnailUrl = "https://cdn/thumb/$name.jpg", fullUrl = "https://cdn/$name.jpg",
        spoiler = false,
    )

    private val ctx = VaultSaveContext(board = "g", threadNo = 100, threadSubject = null, opExcerpt = null, post = null)

    /** Each save records its URL, then suspends until [finish] supplies its result. */
    private open class GatedVault : FakeMediaVault() {
        private val results = Channel<VaultError?>(Channel.UNLIMITED)
        val saveCalls = mutableListOf<String>()

        fun finish(error: VaultError?) { results.trySend(error) }
        override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? {
            saveCalls += item.fullUrl
            return results.receive()
        }
    }

    private fun TestScope.queue(vault: GatedVault = GatedVault()) =
        MediaDownloadQueue(vault, NoDedup, backgroundScope, StandardTestDispatcher(testScheduler))

    private suspend fun MediaDownloadQueue.status(url: String) = statuses.first()[url]

    @Test fun `enqueue walks queued to downloading and disappears on success`() = runTest {
        val vault = GatedVault()
        val queue = queue(vault)
        val a = item("a")
        queue.enqueue(a, ctx)
        runCurrent()
        assertEquals(MediaSaveStatus.Downloading, queue.status(a.fullUrl))
        vault.finish(null)
        runCurrent()
        assertNull(queue.status(a.fullUrl)) // success: entry vanishes, saved table takes over
        assertEquals(listOf(a.fullUrl), vault.saveCalls)
    }

    @Test fun `a failed save keeps a failed marker with the error`() = runTest {
        val vault = GatedVault()
        val queue = queue(vault)
        val a = item("a")
        queue.enqueue(a, ctx)
        vault.finish(VaultError.NoAccess)
        runCurrent()
        assertEquals(MediaSaveStatus.Failed(VaultError.NoAccess), queue.status(a.fullUrl))
    }

    @Test fun `second item waits queued behind the one downloading`() = runTest {
        val vault = GatedVault()
        val queue = queue(vault)
        val a = item("a")
        val b = item("b")
        queue.enqueue(a, ctx)
        runCurrent()
        queue.enqueue(b, ctx)
        runCurrent()
        assertEquals(MediaSaveStatus.Queued, queue.status(b.fullUrl))
        vault.finish(null)
        runCurrent()
        assertEquals(MediaSaveStatus.Downloading, queue.status(b.fullUrl))
        vault.finish(null)
        runCurrent()
        assertNull(queue.status(b.fullUrl))
        assertEquals(listOf(a.fullUrl, b.fullUrl), vault.saveCalls)
    }

    @Test fun `enqueueing the same url twice downloads it once`() = runTest {
        val vault = GatedVault()
        val queue = queue(vault)
        val blocker = item("blocker")
        val a = item("a")
        queue.enqueue(blocker, ctx)
        runCurrent()
        queue.enqueue(a, ctx)
        queue.enqueue(a, ctx) // duplicate while queued: no-op
        vault.finish(null)
        runCurrent()
        assertEquals(MediaSaveStatus.Downloading, queue.status(a.fullUrl))
        queue.enqueue(a, ctx) // duplicate while downloading: no-op
        vault.finish(null)
        runCurrent()
        assertNull(queue.status(a.fullUrl))
        assertEquals(listOf(blocker.fullUrl, a.fullUrl), vault.saveCalls)
    }

    @Test fun `cancel removes a queued entry and the worker skips it`() = runTest {
        val vault = GatedVault()
        val queue = queue(vault)
        val a = item("a")
        val b = item("b")
        val c = item("c")
        queue.enqueue(a, ctx)
        runCurrent()
        queue.enqueue(b, ctx)
        queue.cancel(b.fullUrl)
        assertNull(queue.status(b.fullUrl))
        queue.enqueue(c, ctx)
        vault.finish(null)
        vault.finish(null)
        runCurrent()
        assertNull(queue.status(c.fullUrl))
        assertEquals(listOf(a.fullUrl, c.fullUrl), vault.saveCalls)
    }

    @Test fun `cancel does not touch an entry already downloading`() = runTest {
        val vault = GatedVault()
        val queue = queue(vault)
        val a = item("a")
        queue.enqueue(a, ctx)
        runCurrent()
        assertEquals(MediaSaveStatus.Downloading, queue.status(a.fullUrl))
        queue.cancel(a.fullUrl)
        assertEquals(MediaSaveStatus.Downloading, queue.status(a.fullUrl))
        vault.finish(null)
        runCurrent()
        assertNull(queue.status(a.fullUrl))
    }

    @Test fun `dismiss failed clears the marker without retrying`() = runTest {
        val vault = GatedVault()
        val queue = queue(vault)
        val a = item("a")
        queue.enqueue(a, ctx)
        vault.finish(VaultError.Io("boom"))
        runCurrent()
        assertEquals(MediaSaveStatus.Failed(VaultError.Io("boom")), queue.status(a.fullUrl))
        queue.dismiss(a.fullUrl)
        runCurrent()
        assertNull(queue.status(a.fullUrl))
        assertEquals(listOf(a.fullUrl), vault.saveCalls)
    }

    @Test fun `retry re-queues a failed entry with its original context`() = runTest {
        val vault = GatedVault()
        val queue = queue(vault)
        val a = item("a")
        queue.enqueue(a, ctx)
        vault.finish(VaultError.Io("boom"))
        runCurrent()
        assertEquals(MediaSaveStatus.Failed(VaultError.Io("boom")), queue.status(a.fullUrl))
        queue.retry("https://cdn/never-asked.jpg") // unknown: nothing happens
        queue.retry(a.fullUrl)
        vault.finish(null)
        runCurrent()
        assertNull(queue.status(a.fullUrl))
        assertEquals(listOf(a.fullUrl, a.fullUrl), vault.saveCalls)
    }

    @Test fun `a url the vault already holds reads as saved`() = runTest {
        val vault = object : GatedVault() {
            override fun saved(): Flow<Map<String, String?>> = flowOf(mapOf("https://cdn/a.jpg" to "/v/a.jpg"))
        }
        val queue = queue(vault)
        assertEquals(MediaSaveStatus.Saved, queue.status("https://cdn/a.jpg"))
    }

    @Test fun `a failed entry can be re-enqueued to retry`() = runTest {
        val vault = GatedVault()
        val queue = queue(vault)
        val a = item("a")
        queue.enqueue(a, ctx)
        vault.finish(VaultError.Io("boom"))
        runCurrent()
        assertEquals(MediaSaveStatus.Failed(VaultError.Io("boom")), queue.status(a.fullUrl))
        queue.enqueue(a, ctx)
        vault.finish(null)
        runCurrent()
        assertEquals(listOf(a.fullUrl, a.fullUrl), vault.saveCalls)
        assertTrue(queue.statuses.first().isEmpty())
    }
}

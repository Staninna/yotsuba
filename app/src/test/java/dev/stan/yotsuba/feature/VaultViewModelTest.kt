package dev.stan.yotsuba.feature

import app.cash.turbine.test
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.fake.FakeSettings
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.feature.vault.VaultUiState
import dev.stan.yotsuba.feature.vault.VaultSyncState
import dev.stan.yotsuba.feature.vault.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun entry(
        url: String,
        location: VaultLocation = VaultLocation("g", 100),
        savedAt: Long = 0,
        subject: String? = null,
    ) = VaultEntry(
        url = url, location = location, subject = subject, postNo = null, displayName = url.substringAfterLast('/'),
        absolutePath = "/vault/$url", ext = ".jpg", sizeBytes = 1, width = 1, height = 1,
        thumbnailUrl = null, savedAt = savedAt,
    )

    private class FakeVault(initial: List<VaultEntry>) : MediaVaultRepository {
        val state = MutableStateFlow(initial)
        val deleted = mutableListOf<String>()
        var rescans = 0
        var migrations = 0
        var access = true
        override fun hasStorageAccess() = access
        override fun entries(): Flow<List<VaultEntry>> = state
        override fun savedUrls(): Flow<Set<String>> = state.map { list -> list.map { it.url }.toSet() }
        override fun savedPaths(): Flow<Map<String, String>> =
            state.map { list -> list.associate { it.url to it.absolutePath } }
        override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? = null
        override suspend fun delete(url: String): VaultError? {
            deleted += url
            state.value = state.value.filterNot { it.url == url }
            return null
        }
        var rescanGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        var syncs = 0
        var syncSummary = VaultSyncSummary()
        var syncSteps = 0
        var syncGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        override suspend fun syncSavedThreads(onProgress: (Int, Int) -> Unit): VaultSyncSummary {
            syncs++
            repeat(syncSteps) { onProgress(it + 1, syncSteps) }
            syncGate?.await()
            return syncSummary
        }
        var imported: Pair<String, List<ImportSource>>? = null
        override suspend fun importLocalThread(name: String, sources: List<ImportSource>): VaultError? {
            imported = name to sources
            return importError
        }
        var importError: VaultError? = null
        override suspend fun savedThread(board: String, threadNo: Long): ThreadDetails? = null
        override suspend fun rescan() { rescanGate?.await(); rescans++ }
        override suspend fun migrateLegacyIfNeeded() { migrations++ }
    }

    private suspend fun app.cash.turbine.TurbineTestContext<VaultUiState>.latest(): VaultUiState {
        dispatcher.scheduler.advanceUntilIdle()
        return expectMostRecentItem()
    }

    private val threadA = VaultLocation("a", 200)
    private val threadG = VaultLocation("g", 100)

    @Test fun `a thread is identified by board and number, never by subject`() =
        runTest(dispatcher.scheduler) {
            val vault = FakeVault(listOf(
                entry("g/1.jpg", threadG, subject = null),
                entry("g/2.jpg", threadG, subject = "renamed"),
                entry("g/3.jpg", VaultLocation("g", 101), subject = "renamed"),
                entry("loose.jpg", VaultLocation.Unsorted),
            ))
            VaultViewModel(vault, FakeSettings()).uiState.test {
                val state = latest()
                val g = state.boards.first { it.board == "g" }
                assertEquals(listOf(VaultLocation("g", 101), threadG), g.threads.map { it.location })
                assertEquals("renamed", g.threads.last().subject)
                assertEquals(2, g.threads.last().entries.size)
                assertEquals(VaultLocation.Unsorted, state.boards.first().threads.single().location)
                assertTrue(VaultLocation.Unsorted.isUnsorted)
                assertTrue(VaultLocation("_local", 1).isLocal)
                assertTrue(threadG.isRemote)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `entries group into boards with unsorted sorted first`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(listOf(
            entry("g/1.jpg", threadG),
            entry("a/1.jpg", threadA),
            entry("loose.jpg", VaultLocation.Unsorted),
        ))
        VaultViewModel(vault, FakeSettings()).uiState.test {
            val state = latest()
            assertEquals(listOf("_unsorted", "a", "g"), state.boards.map { it.board })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `drill-down selection scopes the visible entries and navigates back up`() =
        runTest(dispatcher.scheduler) {
            val vault = FakeVault(listOf(entry("g/1.jpg", threadG), entry("a/1.jpg", threadA)))
            val vm = VaultViewModel(vault, FakeSettings())
            vm.uiState.test {
                assertEquals(2, latest().scopeEntries.size)
                vm.openBoard("g")
                vm.openThread(threadG)
                val inThread = latest()
                assertEquals(listOf("g/1.jpg"), inThread.scopeEntries.map { it.url })
                vm.navigateUp()
                val inBoard = latest()
                assertEquals("g", inBoard.selection.board)
                assertNull(inBoard.selection.thread)
                vm.navigateUp()
                assertNull(latest().selection.board)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `viewer opens over the current thread in entry order`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(listOf(entry("g/1.jpg", threadG), entry("g/2.jpg", threadG)))
        val vm = VaultViewModel(vault, FakeSettings())
        vm.openBoard("g")
        vm.openThread(threadG)
        vm.openViewer("g/2.jpg")
        vm.uiState.test {
            val viewer = latest().viewer!!
            assertEquals(listOf("g/1.jpg", "g/2.jpg"), viewer.entries.map { it.url })
            assertEquals(1, viewer.index)
            assertEquals("g/2.jpg", viewer.current.url)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `deleting the viewed entry closes the viewer`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(listOf(entry("g/1.jpg", threadG)))
        val vm = VaultViewModel(vault, FakeSettings())
        vm.openViewer("g/1.jpg")
        vm.uiState.test {
            assertEquals("g/1.jpg", latest().viewer?.current?.url)
            assertNull(vm.delete("g/1.jpg"))
            assertNull(latest().viewer)
            assertEquals(listOf("g/1.jpg"), vault.deleted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `close viewer clears the url and any shuffle order`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(listOf(entry("g/1.jpg", threadG), entry("g/2.jpg", threadG)))
        val vm = VaultViewModel(vault, FakeSettings())
        vm.startShuffle(listOf("g/2.jpg", "g/1.jpg"))
        vm.uiState.test {
            assertTrue(latest().viewer != null)
            vm.closeViewer()
            assertNull(latest().viewer)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `shuffle plays the given urls in the shuffled order`() = runTest(dispatcher.scheduler) {
        val urls = (1..5).map { "g/$it.jpg" }
        val vault = FakeVault(urls.map { entry(it, threadG) })
        val vm = VaultViewModel(vault, FakeSettings())
        vm.startShuffle(urls)
        vm.uiState.test {
            val viewer = latest().viewer!!
            assertEquals(urls.toSet(), viewer.entries.map { it.url }.toSet())
            assertEquals(0, viewer.index)
            assertEquals(viewer.entries.first().url, viewer.current.url)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `paging the viewer moves the index without reordering`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(listOf(entry("g/1.jpg", threadG), entry("g/2.jpg", threadG)))
        val vm = VaultViewModel(vault, FakeSettings())
        vm.openBoard("g")
        vm.openThread(threadG)
        vm.openViewer("g/1.jpg")
        vm.uiState.test {
            assertEquals(0, latest().viewer?.index)
            vm.onViewerPage("g/2.jpg")
            val paged = latest().viewer!!
            assertEquals(1, paged.index)
            assertEquals(listOf("g/1.jpg", "g/2.jpg"), paged.entries.map { it.url })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `viewer opened outside any thread plays just that entry`() =
        runTest(dispatcher.scheduler) {
            val vault = FakeVault(listOf(entry("g/1.jpg", threadG), entry("a/1.jpg", threadA)))
            val vm = VaultViewModel(vault, FakeSettings())
            vm.openViewer("a/1.jpg") // no thread selected — the thread filter matches nothing
            vm.uiState.test {
                val viewer = latest().viewer!!
                assertEquals(listOf("a/1.jpg"), viewer.entries.map { it.url })
                assertEquals(0, viewer.index)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `sync ignores presses while one is already running`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(emptyList())
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        vault.rescanGate = gate
        val vm = VaultViewModel(vault, FakeSettings())
        vm.sync()
        dispatcher.scheduler.advanceUntilIdle() // first pass is now suspended on the gate
        vm.sync() // busy flag must gate this press
        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vault.rescans)
    }

    @Test fun `importing forwards the picked files and reports a failure`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(emptyList())
        val vm = VaultViewModel(vault, FakeSettings())
        val picked = listOf(ImportSource("content://a", "a.jpg"), ImportSource("content://b", "b.webm"))

        assertEquals(null, vm.importLocalThread("Holiday", picked))
        assertEquals("Holiday" to picked, vault.imported)

        vault.importError = VaultError.NoAccess
        assertEquals(VaultError.NoAccess, vm.importLocalThread("Holiday", picked))
    }

    @Test fun `an empty selection is not an import`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(emptyList())
        assertEquals(null, VaultViewModel(vault, FakeSettings()).importLocalThread("Empty", emptyList()))
        assertEquals(null, vault.imported)
    }

    @Test fun `sync shows a live progress counter and clears it when done`() =
        runTest(dispatcher.scheduler) {
            val vault = FakeVault(emptyList())
            vault.syncSteps = 3
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            vault.syncGate = gate
            val vm = VaultViewModel(vault, FakeSettings())

            vm.uiState.test {
                latest()
                vm.sync()
                dispatcher.scheduler.advanceUntilIdle() // held open on the gate, mid-pass
                // The counter is what stops a rate-limited pass over many threads
                // looking hung: it ticks once per thread.
                assertEquals(VaultSyncState(running = true, done = 3, total = 3), latest().sync)

                gate.complete(Unit)
                dispatcher.scheduler.advanceUntilIdle()
                assertEquals(VaultSyncState(), latest().sync)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `the summary comes back to the caller so it can be reported`() =
        runTest(dispatcher.scheduler) {
            val vault = FakeVault(emptyList())
            vault.syncSummary = VaultSyncSummary(updated = 4, gone = 2, failed = 1, rateLimited = true)
            var reported: VaultSyncSummary? = null

            VaultViewModel(vault, FakeSettings()).sync { reported = it }
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(7, reported?.checked)
            assertEquals(true, reported?.rateLimited)
        }

    @Test fun `sync migrates, rebuilds the index, then refreshes live threads`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(emptyList())
        val vm = VaultViewModel(vault, FakeSettings())
        vm.sync()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vault.migrations)
        assertEquals(1, vault.rescans)
        assertEquals(1, vault.syncs)
        assertTrue(vm.hasStorageAccess())
        vault.access = false
        assertEquals(false, vm.hasStorageAccess())
    }
}

package dev.stan.yotsuba.feature

import app.cash.turbine.test
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.fake.FakeSettings
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.feature.vault.VaultBoardKey
import dev.stan.yotsuba.feature.vault.VaultUiState
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
        location: VaultLocation = VaultLocation.Thread("g", 100, "subj"),
        savedAt: Long = 0,
    ) = VaultEntry(
        url = url, location = location, postNo = null, displayName = url.substringAfterLast('/'),
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

    private val threadA = VaultLocation.Thread("a", 200, null)
    private val threadG = VaultLocation.Thread("g", 100, "subj")

    @Test fun `entries group into boards with unsorted sorted first`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(listOf(
            entry("g/1.jpg", threadG),
            entry("a/1.jpg", threadA),
            entry("loose.jpg", VaultLocation.Unsorted),
        ))
        VaultViewModel(vault, FakeSettings()).uiState.test {
            val state = latest()
            assertEquals(
                listOf(VaultBoardKey.Unsorted, VaultBoardKey.Board("a"), VaultBoardKey.Board("g")),
                state.boards.map { it.key },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `drill-down selection scopes the visible entries and navigates back up`() =
        runTest(dispatcher.scheduler) {
            val vault = FakeVault(listOf(entry("g/1.jpg", threadG), entry("a/1.jpg", threadA)))
            val vm = VaultViewModel(vault, FakeSettings())
            vm.uiState.test {
                assertEquals(2, latest().scopeEntries.size)
                vm.openBoard(VaultBoardKey.Board("g"))
                vm.openThread(threadG)
                val inThread = latest()
                assertEquals(listOf("g/1.jpg"), inThread.scopeEntries.map { it.url })
                vm.navigateUp()
                val inBoard = latest()
                assertEquals(VaultBoardKey.Board("g"), inBoard.selection.board)
                assertNull(inBoard.selection.thread)
                vm.navigateUp()
                assertNull(latest().selection.board)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `viewer opens over the current thread in entry order`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(listOf(entry("g/1.jpg", threadG), entry("g/2.jpg", threadG)))
        val vm = VaultViewModel(vault, FakeSettings())
        vm.openBoard(VaultBoardKey.Board("g"))
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
        vm.openBoard(VaultBoardKey.Board("g"))
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

    @Test fun `rescan ignores presses while one is already running`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(emptyList())
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        vault.rescanGate = gate
        val vm = VaultViewModel(vault, FakeSettings())
        vm.rescan()
        dispatcher.scheduler.advanceUntilIdle() // first rescan is now suspended on the gate
        vm.rescan() // busy flag must gate this press
        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vault.rescans)
    }

    @Test fun `rescan migrates legacy files then rebuilds the index`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(emptyList())
        val vm = VaultViewModel(vault, FakeSettings())
        vm.rescan()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vault.migrations)
        assertEquals(1, vault.rescans)
        assertTrue(vm.hasStorageAccess())
        vault.access = false
        assertEquals(false, vm.hasStorageAccess())
    }
}

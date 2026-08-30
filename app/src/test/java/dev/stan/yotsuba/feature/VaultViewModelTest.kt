package dev.stan.yotsuba.feature

import app.cash.turbine.test
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.MediaAutoplay
import dev.stan.yotsuba.core.text.PostText
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.fake.FakeSettings
import dev.stan.yotsuba.core.vault.VaultPaths
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.feature.vault.VaultNotice
import dev.stan.yotsuba.feature.vault.VaultPlayback
import dev.stan.yotsuba.feature.vault.VaultUiState
import dev.stan.yotsuba.feature.vault.VaultSyncState
import dev.stan.yotsuba.feature.vault.VaultViewModel
import dev.stan.yotsuba.feature.vault.UNDO_WINDOW_MS
import dev.stan.yotsuba.feature.vault.VaultFilter
import dev.stan.yotsuba.feature.vault.VaultImport
import dev.stan.yotsuba.feature.vault.VaultMode
import dev.stan.yotsuba.feature.vault.RECENT_LIMIT
import dev.stan.yotsuba.feature.vault.VaultSort
import androidx.lifecycle.SavedStateHandle
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
        sizeBytes: Long? = 1,
        postNo: Long? = null,
    ) = VaultEntry(
        url = url, location = location, subject = subject, postNo = postNo, displayName = url.substringAfterLast('/'),
        absolutePath = "/vault/$url", ext = VaultPaths.extensionOf(url).ifEmpty { ".jpg" }, sizeBytes = sizeBytes,
        width = 1, height = 1, thumbnailUrl = null, savedAt = savedAt,
    )

    private class FakeVault(initial: List<VaultEntry>) : MediaVaultRepository {
        val state = MutableStateFlow(initial)
        val deleted = mutableListOf<String>()
        var rescans = 0
        var migrations = 0
        val access = MutableStateFlow(true)
        override fun hasStorageAccess() = access.value
        override val storageAccess: Flow<Boolean> = access
        override fun entries(): Flow<List<VaultEntry>> = state
        override fun saved(): Flow<Map<String, String?>> =
            state.map { list -> list.associate { it.url to it.absolutePath } }
        override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? = null
        var deleteError: VaultError? = null
        override suspend fun delete(url: String): VaultError? {
            deleteError?.let { return it }
            deleted += url
            state.value = state.value.filterNot { it.url == url }
            return null
        }
        val trash = mutableMapOf<String, VaultEntry>()
        var purges = 0
        override suspend fun trash(url: String): VaultError? {
            deleteError?.let { return it }
            val entry = state.value.firstOrNull { it.url == url } ?: return VaultError.NotFound
            trash[url] = entry
            state.value = state.value.filterNot { it.url == url }
            return null
        }
        override suspend fun restoreTrashed(url: String): VaultError? {
            val entry = trash.remove(url) ?: return VaultError.NotFound
            state.value = state.value + entry
            return null
        }
        override suspend fun purgeTrash() {
            purges++
            trash.clear()
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
        var imports = 0
        var importGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        override suspend fun importLocalThread(name: String, sources: List<ImportSource>): VaultError? {
            imports++
            imported = name to sources
            importGate?.await()
            return importError
        }
        var importError: VaultError? = null
        val threads = mutableMapOf<VaultLocation, ThreadDetails>()
        val threadReads = mutableListOf<VaultLocation>()
        override suspend fun savedThread(board: String, threadNo: Long): ThreadDetails? =
            VaultLocation(board, threadNo).also { threadReads += it }.let { threads[it] }
        override suspend fun rescan() { rescanGate?.await(); rescans++ }
        override suspend fun migrateLegacyIfNeeded() { migrations++ }
    }

    private suspend fun app.cash.turbine.TurbineTestContext<VaultUiState>.latest(): VaultUiState {
        dispatcher.scheduler.advanceUntilIdle()
        return expectMostRecentItem()
    }

    /** Like [latest] but without running the clock forward: for states behind a timer. */
    private suspend fun app.cash.turbine.TurbineTestContext<VaultUiState>.now(): VaultUiState {
        dispatcher.scheduler.runCurrent()
        return expectMostRecentItem()
    }

    private val boards = FakeBoards()

    private fun vm(
        vault: FakeVault,
        settings: FakeSettings = FakeSettings(),
        saved: SavedStateHandle = SavedStateHandle(),
    ) = VaultViewModel(vault, settings, boards, saved, compute = Dispatchers.Unconfined, io = dispatcher)

    private class FakeBoards : BoardRepository {
        val known = mutableMapOf<String, Board>()
        override suspend fun boards(forceRefresh: Boolean): DataResult<List<Board>> =
            DataResult.Success(known.values.toList())
        override suspend fun board(code: String): Board? = known[code]
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
            vm(vault).uiState.test {
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
        vm(vault).uiState.test {
            val state = latest()
            assertEquals(listOf("_unsorted", "a", "g"), state.boards.map { it.board })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `reveal lands on the thread in one write`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(listOf(entry("g/1.jpg", threadG), entry("a/1.jpg", threadA)))
        val vm = vm(vault)
        vm.uiState.test {
            latest()
            vm.reveal(threadA)
            val state = latest()
            assertEquals("a", state.selection.board)
            assertEquals(threadA, state.selection.thread)
            assertEquals(listOf("a/1.jpg"), state.scopeEntries.map { it.url })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `drill-down selection scopes the visible entries and navigates back up`() =
        runTest(dispatcher.scheduler) {
            val vault = FakeVault(listOf(entry("g/1.jpg", threadG), entry("a/1.jpg", threadA)))
            val vm = vm(vault)
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
        val vm = vm(vault)
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
        val vm = vm(vault)
        vm.openViewer("g/1.jpg")
        vm.uiState.test {
            assertEquals("g/1.jpg", latest().viewer?.current?.url)
            vm.requestDelete(entry("g/1.jpg", threadG))
            assertEquals("g/1.jpg", latest().deleting?.single?.url)
            vm.confirmDelete()
            val after = latest()
            assertNull(after.deleting)
            assertNull(after.viewer)
            assertEquals(VaultNotice.Deleted, after.notice)
            assertEquals(listOf("g/1.jpg"), vault.deleted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun details(location: VaultLocation, vararg postNos: Long) = ThreadDetails(
        board = location.board,
        threadNo = location.threadNo,
        posts = postNos.map { no ->
            ThreadPost(
                board = location.board, no = no, isOp = no == postNos.first(), name = "Anonymous",
                tripcode = null, capcode = null, posterId = null, countryCode = null, countryName = null,
                timeSeconds = 0, subject = null, body = PostText(emptyList()), media = null,
                quotedPostNos = emptyList(),
            )
        },
        backlinks = emptyMap(),
        archived = false,
        closed = false,
    )

    @Test fun `viewer thread follows the page across threads and clears on close`() =
        runTest(dispatcher.scheduler) {
            val vault = FakeVault(listOf(entry("g/1.jpg", threadG), entry("a/1.jpg", threadA), entry("g/2.jpg", threadG)))
            vault.threads[threadG] = details(threadG, 100, 101)
            vault.threads[threadA] = details(threadA, 200)
            val vm = vm(vault)
            vm.viewerThread.test {
                assertEquals(false, awaitItem().hasPosts)
                // Shuffle's first page must get its panel too: nothing was "opened" first.
                vm.startShuffle(listOf("g/1.jpg"))
                dispatcher.scheduler.advanceUntilIdle()
                assertEquals(setOf(100L, 101L), expectMostRecentItem().byNo.keys)
                vm.onViewerPage("a/1.jpg")
                dispatcher.scheduler.advanceUntilIdle()
                assertEquals(setOf(200L), expectMostRecentItem().byNo.keys)
                vm.onViewerPage("g/2.jpg")
                vm.onViewerPage("g/1.jpg") // same thread: no re-read
                dispatcher.scheduler.advanceUntilIdle()
                assertEquals(setOf(100L, 101L), expectMostRecentItem().byNo.keys)
                assertEquals(listOf(threadG, threadA, threadG), vault.threadReads)
                vm.closeViewer()
                dispatcher.scheduler.advanceUntilIdle()
                assertEquals(false, expectMostRecentItem().hasPosts)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `cancelling the delete dialog deletes nothing`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(listOf(entry("g/1.jpg", threadG)))
        val vm = vm(vault)
        vm.uiState.test {
            vm.requestDelete(entry("g/1.jpg", threadG))
            assertTrue(latest().deleting != null)
            vm.cancelDelete()
            assertNull(latest().deleting)
            vm.confirmDelete() // nothing queued: a no-op
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(vault.deleted.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `a failed delete keeps the entry and says so`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(listOf(entry("g/1.jpg", threadG)))
        vault.deleteError = VaultError.Io("disk")
        val vm = vm(vault)
        vm.uiState.test {
            vm.requestDelete(entry("g/1.jpg", threadG))
            vm.confirmDelete()
            val after = latest()
            assertEquals(1, after.entries.size)
            assertEquals(VaultNotice.DeleteFailed(entry("g/1.jpg", threadG), VaultError.Io("disk")), after.notice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `a grid delete goes through the trash and comes back on undo`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(listOf(entry("g/1.jpg", threadG), entry("g/2.jpg", threadG)))
        val vm = vm(vault)
        vm.uiState.test {
            latest()
            vm.requestDelete(entry("g/1.jpg", threadG), undoable = true)
            vm.confirmDelete()
            val trashed = now()
            assertEquals(listOf("g/2.jpg"), trashed.entries.map { it.url })
            assertEquals(listOf("g/1.jpg"), trashed.undo?.map { it.url })
            assertTrue(vault.deleted.isEmpty())
            assertEquals(setOf("g/1.jpg"), vault.trash.keys)

            vm.undoDelete()
            val restored = latest()
            assertNull(restored.undo)
            assertEquals(setOf("g/1.jpg", "g/2.jpg"), restored.entries.map { it.url }.toSet())
            assertEquals(VaultNotice.Restored, restored.notice)
            assertTrue(vault.trash.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `the undo window closes on its own and empties the trash`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(listOf(entry("g/1.jpg", threadG)))
        val vm = vm(vault)
        vm.uiState.test {
            latest()
            vm.requestDelete(entry("g/1.jpg", threadG), undoable = true)
            vm.confirmDelete()
            assertEquals(1, now().undo?.size)
            val purgesBefore = vault.purges
            dispatcher.scheduler.advanceTimeBy(UNDO_WINDOW_MS + 1)
            assertNull(latest().undo)
            assertEquals(purgesBefore + 1, vault.purges)
            assertTrue(vault.trash.isEmpty())
            vm.undoDelete() // too late: nothing to bring back
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(vault.state.value.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `don't ask again turns the confirmation off and later deletes skip the dialog`() =
        runTest(dispatcher.scheduler) {
            val vault = FakeVault(listOf(entry("g/1.jpg", threadG), entry("g/2.jpg", threadG)))
            val settings = FakeSettings()
            val vm = vm(vault, settings)
            vm.uiState.test {
                latest()
                vm.requestDelete(entry("g/1.jpg", threadG))
                assertTrue(latest().deleting != null)
                vm.confirmDelete(dontAskAgain = true)
                latest()
                assertEquals(false, settings.state.value.confirmVaultDelete)
                assertEquals(listOf("g/1.jpg"), vault.deleted)

                vm.requestDelete(entry("g/2.jpg", threadG))
                val after = latest()
                assertNull(after.deleting)
                assertEquals(listOf("g/1.jpg", "g/2.jpg"), vault.deleted)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `storage totals add up per thread, per board and overall`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(listOf(
            entry("g/1.jpg", threadG, sizeBytes = 100),
            entry("g/2.jpg", threadG, sizeBytes = 50),
            entry("g/3.jpg", VaultLocation("g", 101), sizeBytes = 25),
            entry("a/1.jpg", threadA, sizeBytes = null), // unknown size counts as nothing
        ))
        vm(vault).uiState.test {
            val state = latest()
            val g = state.boards.first { it.board == "g" }
            assertEquals(150L, g.threads.first { it.location == threadG }.sizeBytes)
            assertEquals(175L, g.sizeBytes)
            assertEquals(0L, state.boards.first { it.board == "a" }.sizeBytes)
            assertEquals(175L, state.totalBytes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `sort and filter shape the visible entries and survive in saved state`() =
        runTest(dispatcher.scheduler) {
            val vault = FakeVault(listOf(
                entry("g/b.jpg", threadG, savedAt = 1, sizeBytes = 10, postNo = 3),
                entry("g/a.webm", threadG, savedAt = 3, sizeBytes = 30, postNo = 1),
                entry("g/c.jpg", threadG, savedAt = 2, sizeBytes = 20, postNo = null),
            ))
            val saved = SavedStateHandle()
            val vm = vm(vault, saved = saved)
            vm.uiState.test {
                assertEquals(listOf("g/a.webm", "g/c.jpg", "g/b.jpg"), latest().visible.map { it.url })
                vm.setSort(VaultSort.NAME)
                assertEquals(listOf("g/a.webm", "g/b.jpg", "g/c.jpg"), latest().visible.map { it.url })
                vm.setSort(VaultSort.SIZE)
                assertEquals(listOf("g/a.webm", "g/c.jpg", "g/b.jpg"), latest().visible.map { it.url })
                vm.setSort(VaultSort.POST)
                assertEquals(listOf("g/a.webm", "g/b.jpg", "g/c.jpg"), latest().visible.map { it.url })
                vm.toggleReversed()
                assertEquals(listOf("g/c.jpg", "g/b.jpg", "g/a.webm"), latest().visible.map { it.url })
                assertEquals(true, saved.get<Boolean>("vault_reversed"))
                vm.toggleReversed()
                assertEquals(listOf("g/a.webm", "g/b.jpg", "g/c.jpg"), latest().visible.map { it.url })
                vm.setFilter(VaultFilter.IMAGES)
                assertEquals(listOf("g/b.jpg", "g/c.jpg"), latest().visible.map { it.url })
                vm.setFilter(VaultFilter.VIDEOS)
                val videos = latest()
                assertEquals(listOf("g/a.webm"), videos.visible.map { it.url })
                assertEquals(1, videos.boards.single().threads.single().entries.size)
                assertEquals(3, videos.entries.size)
                cancelAndIgnoreRemainingEvents()
            }
            // A fresh VM over the same handle -- process death -- picks the choice back up.
            vm(vault, saved = saved).uiState.test {
                val restored = latest()
                assertEquals(VaultSort.POST, restored.sort)
                assertEquals(VaultFilter.VIDEOS, restored.filter)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `recent is the newest two hundred and the viewer pages through it`() =
        runTest(dispatcher.scheduler) {
            val all = (1..250).map { entry("g/$it.jpg", VaultLocation("g", (it % 7).toLong()), savedAt = it.toLong()) }
            val vault = FakeVault(all)
            val vm = vm(vault)
            vm.uiState.test {
                val state = latest()
                assertEquals(VaultMode.RECENT, state.mode)
                assertEquals(RECENT_LIMIT, state.recent.size)
                assertEquals("g/250.jpg", state.recent.first().url)
                assertEquals("g/51.jpg", state.recent.last().url)
                assertEquals(RECENT_LIMIT, state.scopeEntries.size)

                vm.openViewer("g/249.jpg")
                val viewer = latest().viewer!!
                assertEquals(RECENT_LIMIT, viewer.entries.size)
                assertEquals(1, viewer.index)

                vm.setMode(VaultMode.BROWSE)
                assertEquals(250, latest().scopeEntries.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `search matches file names and thread subjects across the vault`() =
        runTest(dispatcher.scheduler) {
            val vault = FakeVault(listOf(
                entry("g/cat_photo.jpg", threadG, subject = "Pets general"),
                entry("g/dog.jpg", threadG, subject = "Pets general"),
                entry("a/CAT.webm", threadA, subject = "Anime"),
                entry("a/other.jpg", threadA, subject = null),
            ))
            val vm = vm(vault)
            vm.openBoard("a") // search reaches past the drill-down
            vm.uiState.test {
                assertNull(latest().results)
                vm.setQuery("cat")
                val byName = latest()
                assertEquals(setOf("g/cat_photo.jpg", "a/CAT.webm"), byName.results?.map { it.url }?.toSet())
                assertEquals(byName.results, byName.scopeEntries)
                vm.setQuery("pets")
                assertEquals(setOf("g/cat_photo.jpg", "g/dog.jpg"), latest().results?.map { it.url }?.toSet())
                vm.setQuery("zzz")
                assertEquals(emptyList<VaultEntry>(), latest().results)
                vm.setQuery("  ")
                assertNull(latest().results)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `playback follows the autoplay setting and the board's webm audio`() =
        runTest(dispatcher.scheduler) {
            val vault = FakeVault(listOf(entry("g/1.jpg", threadG), entry("wsg/1.webm", VaultLocation("wsg", 5))))
            boards.known["wsg"] = Board(
                code = "wsg", title = "wsg", description = "", worksafe = true,
                category = BoardCategory.INTERESTS, userIds = false, countryFlags = false,
                boardFlags = false, spoilers = false, webmAudio = true, codeTags = false,
                mathTags = false, sjisTags = false, textOnly = false,
            )
            val settings = FakeSettings()
            val vm = vm(vault, settings)
            vm.playback.test {
                assertEquals(VaultPlayback(muted = true, playing = true), awaitItem())
                vm.openViewer("wsg/1.webm")
                dispatcher.scheduler.advanceUntilIdle()
                assertEquals(VaultPlayback(muted = false, playing = true), expectMostRecentItem())
                settings.update { it.copy(mediaAutoplay = MediaAutoplay.NEVER) }
                dispatcher.scheduler.advanceUntilIdle()
                assertEquals(VaultPlayback(muted = false, playing = false), expectMostRecentItem())
                vm.onViewerPage("g/1.jpg")
                dispatcher.scheduler.advanceUntilIdle()
                assertEquals(VaultPlayback(muted = true, playing = false), expectMostRecentItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `close viewer clears the url and any shuffle order`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(listOf(entry("g/1.jpg", threadG), entry("g/2.jpg", threadG)))
        val vm = vm(vault)
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
        val vm = vm(vault)
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
        val vm = vm(vault)
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
            val vm = vm(vault)
            vm.setMode(VaultMode.BROWSE) // off the Recent feed, with no thread selected
            vm.openViewer("a/1.jpg") // the thread filter matches nothing
            vm.uiState.test {
                val viewer = latest().viewer!!
                assertEquals(listOf("a/1.jpg"), viewer.entries.map { it.url })
                assertEquals(0, viewer.index)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `a sync ignores presses while one is already running`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(emptyList())
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        vault.rescanGate = gate
        val vm = vm(vault)
        vm.rescan()
        dispatcher.scheduler.advanceUntilIdle() // first pass is now suspended on the gate
        vm.rescan() // busy flag must gate this press
        vm.fetchReplies() // and the other kind of sync too
        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vault.rescans)
        assertEquals(0, vault.syncs)
    }

    @Test fun `importing forwards the picked files and reports a failure`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(emptyList())
        val vm = vm(vault)
        val picked = listOf(ImportSource("content://a", "a.jpg"), ImportSource("content://b", "b.webm"))

        vm.uiState.test {
            vm.importLocalThread("Holiday", picked)
            val done = latest()
            assertEquals("Holiday" to picked, vault.imported)
            assertEquals(false, done.importing)
            assertNull(done.notice)

            vault.importError = VaultError.NoAccess
            vm.importLocalThread("Holiday", picked)
            assertEquals(VaultNotice.ImportFailed(VaultError.NoAccess), latest().notice)
            vm.noticeShown()
            assertNull(latest().notice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `a picker result is resolved on the io dispatcher before the copy`() =
        runTest(dispatcher.scheduler) {
            val vault = FakeVault(emptyList())
            val vm = vm(vault)
            val picked = listOf(ImportSource("content://a", "a.jpg"))
            var resolves = 0
            vm.uiState.test {
                vm.importLocalThread { resolves++; VaultImport("Folder", picked) }
                val done = latest()
                assertEquals(1, resolves)
                assertEquals("Folder" to picked, vault.imported)
                assertEquals(false, done.importing)

                vm.importLocalThread { resolves++; VaultImport("Nothing", emptyList()) }
                assertEquals(VaultNotice.ImportEmpty, latest().notice)
                assertEquals(1, vault.imports)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `an empty selection is not an import`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(emptyList())
        val vm = vm(vault)
        vm.uiState.test {
            vm.importLocalThread("Empty", emptyList())
            assertEquals(VaultNotice.ImportEmpty, latest().notice)
            assertEquals(null, vault.imported)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `a second import is ignored while one is copying`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(emptyList())
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        vault.importGate = gate
        val vm = vm(vault)
        val picked = listOf(ImportSource("content://a", "a.jpg"))
        vm.uiState.test {
            vm.importLocalThread("One", picked)
            assertTrue(latest().importing)
            vm.importLocalThread("Two", picked)
            gate.complete(Unit)
            assertEquals(false, latest().importing)
            assertEquals(1, vault.imports)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `fetching replies shows a live progress counter and clears it when done`() =
        runTest(dispatcher.scheduler) {
            val vault = FakeVault(emptyList())
            vault.syncSteps = 3
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            vault.syncGate = gate
            val vm = vm(vault)

            vm.uiState.test {
                latest()
                vm.fetchReplies()
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

            vm(vault).fetchReplies { reported = it }
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(7, reported?.checked)
            assertEquals(true, reported?.rateLimited)
        }

    @Test fun `rescan migrates before it rebuilds the index`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(emptyList())
        val vm = vm(vault)
        vm.rescan()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vault.migrations)
        assertEquals(1, vault.rescans)
        assertEquals(0, vault.syncs)
    }

    @Test fun `rescan touches only the index and fetch only the network`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(emptyList())
        vault.syncSummary = VaultSyncSummary(updated = 2)
        val vm = vm(vault)
        var rescanned = false
        vm.rescan { rescanned = true }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vault.rescans)
        assertEquals(0, vault.syncs)
        assertTrue(rescanned)

        var summary: VaultSyncSummary? = null
        vm.fetchReplies { summary = it }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vault.rescans)
        assertEquals(1, vault.syncs)
        assertEquals(2, summary?.updated)
    }

    @Test fun `the seed state is not ready and the first real one is`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(listOf(entry("g/1.jpg", threadG)))
        val vm = vm(vault)
        assertEquals(false, vm.uiState.value.ready)
        vm.uiState.test {
            assertTrue(latest().ready)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `stats are null until the vault has been read, then follow the grid's snapshot`() =
        runTest(dispatcher.scheduler) {
            val vault = FakeVault(listOf(entry("g/1.jpg", threadG, sizeBytes = 5), entry("a/1.jpg", threadA, sizeBytes = 7)))
            val vm = vm(vault)
            assertNull(vm.stats.value)
            vm.stats.test {
                dispatcher.scheduler.advanceUntilIdle()
                val stats = expectMostRecentItem()!!
                assertEquals(2, stats.files)
                assertEquals(listOf("a", "g"), stats.perBoard.map { it.board })
                vault.state.value = vault.state.value.take(1)
                dispatcher.scheduler.advanceUntilIdle()
                assertEquals(1, expectMostRecentItem()?.files)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `storage access is part of the ui state`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault(emptyList())
        vm(vault).uiState.test {
            assertTrue(latest().hasStorageAccess)
            vault.access.value = false
            assertEquals(false, latest().hasStorageAccess)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

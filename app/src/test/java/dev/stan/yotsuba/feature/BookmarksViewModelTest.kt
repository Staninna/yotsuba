package dev.stan.yotsuba.feature

import app.cash.turbine.test
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkSortOrder
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.repository.BookmarkRefreshSummary
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.fake.FakeMediaVault
import dev.stan.yotsuba.fake.MainDispatcherRule
import dev.stan.yotsuba.feature.bookmarks.BookmarksViewModel
import dev.stan.yotsuba.feature.bookmarks.RefreshProgress
import dev.stan.yotsuba.feature.bookmarks.SnapshotResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private fun bookmark(
        no: Long,
        unread: Int = 0,
        activity: Long? = null,
        pinned: Boolean = false,
        state: BookmarkState = BookmarkState.ALIVE,
    ) = Bookmark(
        board = "g", threadNo = no, subject = null, opExcerpt = "e", thumbnailUrl = null,
        replyCount = 0, imageCount = 0, bookmarkedAt = no, lastCheckedAt = null,
        state = state,
        readUpTo = 0, postNos = List(unread) { it + 1L }, pinned = pinned, lastActivityAt = activity,
    )

    private class FakeRepo(initial: List<Bookmark>) : BookmarkRepository {
        val state = MutableStateFlow(initial)
        var refreshAllCalls = 0
        var removeDeadCalls = 0
        var gate: CompletableDeferred<Unit>? = null
        override val bookmarks: Flow<List<Bookmark>> get() = state
        override suspend fun add(bookmark: Bookmark) { state.value = state.value + bookmark }
        override suspend fun remove(board: String, threadNo: Long) {
            state.value = state.value.filterNot { it.board == board && it.threadNo == threadNo }
        }
        override fun isBookmarked(board: String, threadNo: Long) = flowOf(true)
        override suspend fun refreshAll(onProgress: (Int, Int) -> Unit): BookmarkRefreshSummary {
            refreshAllCalls++
            onProgress(0, 2)
            gate?.await()
            onProgress(2, 2)
            return BookmarkRefreshSummary()
        }
        override suspend fun markSeen(board: String, threadNo: Long, postNo: Long) {}
        override suspend fun setPinned(board: String, threadNo: Long, pinned: Boolean) {
            state.value = state.value.map { if (it.threadNo == threadNo) it.copy(pinned = pinned) else it }
        }
        override suspend fun removeDead() { removeDeadCalls++ }
        override suspend fun clearAll() { state.value = emptyList() }
    }

    private class FakeVault : FakeMediaVault() {
        val snapshots = mutableListOf<Pair<String, Long>>()
        var snapshotError: VaultError? = null
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun snapshotThread(board: String, threadNo: Long): VaultError? {
            snapshots += board to threadNo
            gate?.await()
            return snapshotError
        }
    }

    private class FakeSettings : SettingsRepository {
        override val settings = MutableStateFlow(Settings())
        override suspend fun update(transform: (Settings) -> Settings) = settings.update(transform)
    }

    private fun vm(repo: BookmarkRepository, vault: MediaVaultRepository = FakeVault(), settings: FakeSettings = FakeSettings()) =
        BookmarksViewModel(repo, vault, settings)

    @Test fun `sort order is written to settings and read back from them`() = runTest(dispatcher.scheduler) {
        val settings = FakeSettings()
        val vm = vm(FakeRepo(listOf(bookmark(1))), settings = settings)
        vm.uiState.test {
            assertEquals(BookmarkSortOrder.UNREAD_FIRST, awaitItem().sortOrder)
            vm.onSortOrderChanged(BookmarkSortOrder.BOOKMARKED)
            assertEquals(BookmarkSortOrder.BOOKMARKED, awaitItem().sortOrder)
            assertEquals(BookmarkSortOrder.BOOKMARKED, settings.settings.value.bookmarkSortOrder)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `snapshot calls the vault and reports success`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault().apply { gate = CompletableDeferred() }
        val vm = vm(FakeRepo(listOf(bookmark(1))), vault)
        vm.uiState.test {
            awaitItem()
            vm.snapshot("g", 1)
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(expectMostRecentItem().isSnapshotting(bookmark(1)))
            vault.gate!!.complete(Unit)
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(!expectMostRecentItem().isSnapshotting(bookmark(1)))
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(SnapshotResult.Saved, vm.snapshotResult.value)
        assertEquals(listOf("g" to 1L), vault.snapshots)
    }

    @Test fun `snapshot surfaces the vault error`() = runTest(dispatcher.scheduler) {
        val vault = FakeVault().apply { snapshotError = VaultError.NoAccess }
        val vm = vm(FakeRepo(listOf(bookmark(1))), vault)
        vm.snapshot("g", 1)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(SnapshotResult.Failed(VaultError.NoAccess), vm.snapshotResult.value)
    }

    @Test fun `snapshot result waits for a collector and clears once shown`() = runTest(dispatcher.scheduler) {
        val vm = vm(FakeRepo(listOf(bookmark(1))), FakeVault())
        // Nobody collects while the write finishes: the outcome must not be dropped.
        vm.snapshot("g", 1)
        dispatcher.scheduler.advanceUntilIdle()
        vm.snapshotResult.test {
            assertEquals(SnapshotResult.Saved, awaitItem())
            vm.onSnapshotResultShown()
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `refresh-all runs one board-grouped pass and the spinner follows it`() = runTest(dispatcher.scheduler) {
        val repo = FakeRepo(listOf(bookmark(1)))
        repo.gate = CompletableDeferred()
        val vm = vm(repo)
        vm.uiState.test {
            awaitItem()
            vm.onRefreshAll()
            dispatcher.scheduler.advanceUntilIdle()
            expectMostRecentItem().let {
                assertTrue(it.isRefreshing)
                assertEquals(RefreshProgress(0, 2), it.checking)
            }
            repo.gate!!.complete(Unit)
            dispatcher.scheduler.advanceUntilIdle()
            expectMostRecentItem().let {
                assertTrue(!it.isRefreshing)
                assertNull(it.checking)
            }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repo.refreshAllCalls)
    }

    @Test fun `pinned rows lead, then the chosen order applies`() = runTest(dispatcher.scheduler) {
        val repo = FakeRepo(
            listOf(
                bookmark(1, unread = 5, activity = 10),
                bookmark(2, unread = 0, activity = 30, pinned = true),
                bookmark(3, unread = 2, activity = 20),
            ),
        )
        val vm = vm(repo)
        vm.uiState.test {
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf(2L, 1L, 3L), expectMostRecentItem().bookmarks.map { it.threadNo })
            vm.onSortOrderChanged(BookmarkSortOrder.LAST_ACTIVITY)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf(2L, 3L, 1L), expectMostRecentItem().bookmarks.map { it.threadNo })
            vm.onSortOrderChanged(BookmarkSortOrder.BOOKMARKED)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf(2L, 3L, 1L), expectMostRecentItem().bookmarks.map { it.threadNo })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `remove dead is offered only when a pruned row exists`() = runTest(dispatcher.scheduler) {
        val repo = FakeRepo(listOf(bookmark(1), bookmark(2, state = BookmarkState.DEAD)))
        val vm = vm(repo)
        vm.uiState.test {
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(true, expectMostRecentItem().hasDead)
            cancelAndIgnoreRemainingEvents()
        }
        vm.onRemoveDead()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.removeDeadCalls)
    }

    @Test fun `remove and undo restore the bookmark`() = runTest(dispatcher.scheduler) {
        val repo = FakeRepo(listOf(bookmark(1)))
        val vm = vm(repo)
        vm.onRemove(bookmark(1))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, repo.state.value.size)
        vm.onUndoRemove(bookmark(1))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.state.value.size)
    }
}

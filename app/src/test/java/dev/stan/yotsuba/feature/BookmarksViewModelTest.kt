package dev.stan.yotsuba.feature

import app.cash.turbine.test
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.repository.BookmarkRefreshSummary
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.feature.bookmarks.BookmarkSortOrder
import dev.stan.yotsuba.feature.bookmarks.BookmarksViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun bookmark(
        no: Long,
        unread: Int = 0,
        activity: Long? = null,
        pinned: Boolean = false,
        state: BookmarkState = BookmarkState.ALIVE,
    ) = Bookmark(
        board = "g", threadNo = no, subject = null, opExcerpt = "e", thumbnailUrl = null,
        replyCount = 0, imageCount = 0, bookmarkedAt = no, lastCheckedAt = null,
        lastSeenPostNo = null, state = state,
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
        override suspend fun refreshOne(bookmark: Bookmark): Bookmark = bookmark
        override suspend fun refreshAll(onProgress: (Int, Int) -> Unit): BookmarkRefreshSummary {
            refreshAllCalls++
            onProgress(0, 2)
            gate?.await()
            onProgress(2, 2)
            return BookmarkRefreshSummary()
        }
        override suspend fun markSeen(board: String, threadNo: Long, lastSeenPostNo: Long, replyCount: Int) {}
        @Deprecated("Unread is derived from readUpTo; use markSeen")
        override suspend fun updateUnread(board: String, threadNo: Long, unread: Int) {}
        override suspend fun setPinned(board: String, threadNo: Long, pinned: Boolean) {
            state.value = state.value.map { if (it.threadNo == threadNo) it.copy(pinned = pinned) else it }
        }
        override suspend fun removeDead() { removeDeadCalls++ }
        override suspend fun clearAll() { state.value = emptyList() }
    }

    @Test fun `refresh-all runs one board-grouped pass and the spinner follows it`() = runTest(dispatcher.scheduler) {
        val repo = FakeRepo(listOf(bookmark(1)))
        repo.gate = CompletableDeferred()
        val vm = BookmarksViewModel(repo)
        vm.uiState.test {
            awaitItem()
            vm.onRefreshAll()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(0 to 2, expectMostRecentItem().checking)
            repo.gate!!.complete(Unit)
            dispatcher.scheduler.advanceUntilIdle()
            assertNull(expectMostRecentItem().checking)
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
        val vm = BookmarksViewModel(repo)
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
        val vm = BookmarksViewModel(repo)
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
        val vm = BookmarksViewModel(repo)
        vm.onRemove(bookmark(1))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, repo.state.value.size)
        vm.onUndoRemove(bookmark(1))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.state.value.size)
    }
}

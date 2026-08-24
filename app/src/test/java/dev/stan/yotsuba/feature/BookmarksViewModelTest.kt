package dev.stan.yotsuba.feature

import app.cash.turbine.test
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.feature.bookmarks.BookmarksViewModel
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun bookmark(no: Long) = Bookmark(
        board = "g", threadNo = no, subject = null, opExcerpt = "e", thumbnailUrl = null,
        replyCount = 0, imageCount = 0, bookmarkedAt = no, lastCheckedAt = null,
        lastSeenPostNo = null, state = BookmarkState.ALIVE,
    )

    private class FakeRepo(initial: List<Bookmark>) : BookmarkRepository {
        val state = MutableStateFlow(initial)
        val refreshed = mutableListOf<Long>()
        override val bookmarks: Flow<List<Bookmark>> get() = state
        override suspend fun add(bookmark: Bookmark) { state.value = state.value + bookmark }
        override suspend fun remove(board: String, threadNo: Long) {
            state.value = state.value.filterNot { it.board == board && it.threadNo == threadNo }
        }
        override fun isBookmarked(board: String, threadNo: Long) = flowOf(true)
        override suspend fun refreshOne(bookmark: Bookmark): Bookmark {
            refreshed += bookmark.threadNo
            return bookmark
        }
        override suspend fun markSeen(board: String, threadNo: Long, lastSeenPostNo: Long, replyCount: Int) {
            state.value = state.value.map {
                if (it.board == board && it.threadNo == threadNo) {
                    it.copy(lastSeenPostNo = lastSeenPostNo, replyCount = replyCount, newReplies = 0)
                } else it
            }
        }
        override suspend fun updateUnread(board: String, threadNo: Long, unread: Int) {
            state.value = state.value.map {
                if (it.board == board && it.threadNo == threadNo) it.copy(unreadCount = unread) else it
            }
        }
    }

    @Test fun `refresh-all walks every bookmark sequentially`() = runTest(dispatcher.scheduler) {
        val repo = FakeRepo(listOf(bookmark(1), bookmark(2), bookmark(3)))
        val vm = BookmarksViewModel(repo)
        vm.uiState.test {
            awaitItem()
            vm.onRefreshAll()
            dispatcher.scheduler.advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(1L, 2L, 3L), repo.refreshed)
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

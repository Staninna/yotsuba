package dev.stan.yotsuba.feature

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThemeMode
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.MaintenanceRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.feature.settings.SettingsUiState
import dev.stan.yotsuba.feature.settings.SettingsViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric only because the VM reads the app's versionName off a [android.content.Context];
 * everything else is faked at the repository interfaces.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
        val state = MutableStateFlow(initial)
        override val settings: Flow<Settings> = state
        override suspend fun update(transform: (Settings) -> Settings) {
            state.value = transform(state.value)
        }
    }

    private class FakeHistoryRepository : HistoryRepository {
        var cleared = false
        override val history: Flow<List<HistoryEntry>> = flowOf(emptyList())
        override suspend fun record(entry: HistoryEntry) {}
        override suspend fun updateScrollPosition(board: String, threadNo: Long, postNo: Long) {}
        override suspend fun lastScrollPosition(board: String, threadNo: Long): Long? = null
        override suspend fun updateReadUpTo(board: String, threadNo: Long, postNo: Long) {}
        override suspend fun readUpTo(board: String, threadNo: Long): Long? = null
        override suspend fun remove(board: String, threadNo: Long) {}
        override suspend fun clearAll() { cleared = true }
        override suspend fun trim(retainAfterMs: Long) {}
    }

    private class FakeBookmarkRepository : BookmarkRepository {
        var cleared = false
        override val bookmarks: Flow<List<Bookmark>> = flowOf(emptyList())
        override suspend fun add(bookmark: Bookmark) {}
        override suspend fun remove(board: String, threadNo: Long) {}
        override fun isBookmarked(board: String, threadNo: Long) = flowOf(false)
        override suspend fun refreshOne(bookmark: Bookmark) = bookmark
        override suspend fun markSeen(board: String, threadNo: Long, lastSeenPostNo: Long, replyCount: Int) {}
        override suspend fun updateUnread(board: String, threadNo: Long, unread: Int) {}
        override suspend fun clearAll() { cleared = true }
    }

    private class FakeHiddenThreadsRepository(initial: List<HiddenThread> = emptyList()) : HiddenThreadsRepository {
        val state = MutableStateFlow(initial)
        override val all: Flow<List<HiddenThread>> = state
        override fun forBoard(board: String) = state.map { list -> list.filter { it.board == board } }
        override suspend fun hide(board: String, threadNo: Long) {
            state.value = state.value + HiddenThread(board, threadNo)
        }
        override suspend fun unhide(board: String, threadNo: Long) {
            state.value = state.value.filterNot { it.board == board && it.threadNo == threadNo }
        }
    }

    private class FakeBoardRepository(val list: List<Board>) : BoardRepository {
        override suspend fun boards(forceRefresh: Boolean) = DataResult.Success(list)
        override suspend fun board(code: String) = list.firstOrNull { it.code == code }
    }

    private class FakeMaintenanceRepository : MaintenanceRepository {
        var clearCalls = 0
        override suspend fun clearCaches() { clearCalls++ }
    }

    private fun board(code: String, worksafe: Boolean) = Board(
        code = code, title = code, description = "", worksafe = worksafe,
        category = BoardCategory.MISC, userIds = false, countryFlags = false, boardFlags = false,
        spoilers = false, webmAudio = false, codeTags = false, mathTags = false,
        sjisTags = false, textOnly = false,
    )

    private class Env(
        val settings: FakeSettingsRepository = FakeSettingsRepository(),
        val hidden: FakeHiddenThreadsRepository = FakeHiddenThreadsRepository(),
        boards: List<Board> = emptyList(),
    ) {
        val history = FakeHistoryRepository()
        val bookmarks = FakeBookmarkRepository()
        val maintenance = FakeMaintenanceRepository()
        val vm = SettingsViewModel(
            context = ApplicationProvider.getApplicationContext(),
            settingsRepository = settings,
            historyRepository = history,
            bookmarkRepository = bookmarks,
            hiddenThreadsRepository = hidden,
            boardRepository = FakeBoardRepository(boards),
            maintenanceRepository = maintenance,
        )
    }

    private suspend fun app.cash.turbine.TurbineTestContext<SettingsUiState>.latest(): SettingsUiState {
        dispatcher.scheduler.advanceUntilIdle()
        return expectMostRecentItem()
    }

    @Test fun `settings and hidden threads pass through to the ui state`() =
        runTest(dispatcher.scheduler) {
            val env = Env(
                settings = FakeSettingsRepository(Settings(themeMode = ThemeMode.DARK)),
                hidden = FakeHiddenThreadsRepository(listOf(HiddenThread("g", 1))),
            )
            env.vm.uiState.test {
                val state = latest()
                assertEquals(ThemeMode.DARK, state.settings.themeMode)
                assertEquals(listOf(HiddenThread("g", 1)), state.hiddenThreads)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `update writes through the settings repository`() = runTest(dispatcher.scheduler) {
        val env = Env()
        env.vm.update { it.copy(dynamicColor = false) }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, env.settings.state.value.dynamicColor)
    }

    @Test fun `clear cache hits the maintenance repository`() = runTest(dispatcher.scheduler) {
        val env = Env()
        env.vm.onClearCache()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, env.maintenance.clearCalls)
    }

    @Test fun `clear history and clear bookmarks hit their repositories`() =
        runTest(dispatcher.scheduler) {
            val env = Env()
            env.vm.onClearHistory()
            env.vm.onClearBookmarks()
            dispatcher.scheduler.advanceUntilIdle()
            assertTrue(env.history.cleared)
            assertTrue(env.bookmarks.cleared)
        }

    @Test fun `hide nsfw boards adds only non-worksafe codes to hidden boards`() =
        runTest(dispatcher.scheduler) {
            val env = Env(boards = listOf(board("g", worksafe = true), board("b", worksafe = false)))
            env.vm.onHideNsfwBoards()
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(setOf("b"), env.settings.state.value.hiddenBoards)
        }

    @Test fun `unhide thread removes it from the hidden list`() = runTest(dispatcher.scheduler) {
        val env = Env(hidden = FakeHiddenThreadsRepository(listOf(HiddenThread("g", 1), HiddenThread("g", 2))))
        env.vm.onUnhideThread(HiddenThread("g", 1))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(HiddenThread("g", 2)), env.hidden.state.value)
    }

    @Test fun `trusted domains revoke one or clear all`() = runTest(dispatcher.scheduler) {
        val env = Env(settings = FakeSettingsRepository(
            Settings(trustedDomains = setOf("a.com", "b.com"))
        ))
        env.vm.onRevokeTrustedDomain("a.com")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("b.com"), env.settings.state.value.trustedDomains)
        env.vm.onClearTrustedDomains()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptySet<String>(), env.settings.state.value.trustedDomains)
    }
}

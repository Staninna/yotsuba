package dev.stan.yotsuba.feature.catalog

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.stan.yotsuba.core.network.NetworkMonitor
import dev.stan.yotsuba.core.text.PostSegment
import dev.stan.yotsuba.core.text.PostText
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.Filter
import dev.stan.yotsuba.domain.model.FilterAction
import dev.stan.yotsuba.domain.model.FilterField
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.CatalogRepository
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.fake.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric only because [NetworkMonitor] is a concrete class over ConnectivityManager;
 * everything else is faked at the repository interfaces.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class CatalogViewModelTest {

    @get:Rule val main = MainDispatcherRule()
    private val dispatcher get() = main.dispatcher

    private class FakeCatalogRepository(var result: DataResult<List<CatalogThread>>) : CatalogRepository {
        var calls = 0
        var forceFlags = mutableListOf<Boolean>()
        override suspend fun catalog(board: String, forceRefresh: Boolean): DataResult<List<CatalogThread>> {
            calls++
            forceFlags += forceRefresh
            return result
        }
    }

    private object FakeBoardRepository : BoardRepository {
        val g = Board(
            code = "g", title = "Technology", description = "", worksafe = true,
            category = BoardCategory.INTERESTS, userIds = false, countryFlags = false,
            boardFlags = false, spoilers = false, webmAudio = false, codeTags = false,
            mathTags = false, sjisTags = false, textOnly = false,
        )
        override suspend fun boards(forceRefresh: Boolean) = DataResult.Success(listOf(g))
        override suspend fun board(code: String) = g.takeIf { code == "g" }
    }

    private class FakeSettingsRepository : SettingsRepository {
        val state = MutableStateFlow(Settings())
        override val settings: Flow<Settings> = state
        override suspend fun update(transform: (Settings) -> Settings) {
            state.value = transform(state.value)
        }
    }

    private class FakeHiddenThreadsRepository : HiddenThreadsRepository {
        val state = MutableStateFlow<List<HiddenThread>>(emptyList())
        override val all: Flow<List<HiddenThread>> = state
        override fun forBoard(board: String) = state.map { list -> list.filter { it.board == board } }
        override suspend fun hide(board: String, threadNo: Long) {
            state.value = state.value + HiddenThread(board, threadNo)
        }
        override suspend fun unhide(board: String, threadNo: Long) {
            state.value = state.value.filterNot { it.board == board && it.threadNo == threadNo }
        }
    }

    private inner class Env(
        threads: List<CatalogThread> = listOf(thread(1, subject = "Alpha"), thread(2), thread(3)),
        val settings: FakeSettingsRepository = FakeSettingsRepository(),
        val hidden: FakeHiddenThreadsRepository = FakeHiddenThreadsRepository(),
    ) {
        val catalog = FakeCatalogRepository(DataResult.Success(threads))

        fun vm(initialSearch: String? = null) = CatalogViewModel(
            board = "g",
            initialSearch = initialSearch,
            catalogRepository = catalog,
            boardRepository = FakeBoardRepository,
            settingsRepository = settings,
            hiddenThreadsRepository = hidden,
            networkMonitor = NetworkMonitor(ApplicationProvider.getApplicationContext()),
            compute = dispatcher,
        )
    }

    private companion object {
        fun thread(no: Long, subject: String? = null) = CatalogThread(
            board = "g", no = no, subject = subject,
            excerpt = PostText(listOf(PostSegment("excerpt $no"))),
            thumbnailUrl = null, replyCount = 0, imageCount = 0, lastModified = no,
            sticky = false, closed = false,
        )
    }

    private suspend fun app.cash.turbine.TurbineTestContext<UiState<CatalogContent>>.latest(): UiState<CatalogContent> {
        dispatcher.scheduler.advanceUntilIdle()
        return expectMostRecentItem()
    }

    @Test fun `successful load exposes the board info and threads`() = runTest(dispatcher.scheduler) {
        val env = Env()
        val vm = env.vm()
        vm.uiState.test {
            val content = (latest() as UiState.Success).data
            assertEquals(listOf(1L, 2L, 3L), content.threads.map { it.no })
            assertEquals("g", vm.boardInfo.value?.code)
            assertEquals(false, content.refreshing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `failed load surfaces the network error`() = runTest(dispatcher.scheduler) {
        val env = Env()
        env.catalog.result = DataResult.Failure(NetworkError.NotFound)
        env.vm().uiState.test {
            assertEquals(NetworkError.NotFound, (latest() as UiState.Error).error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `refresh forces the fetch and picks up new data`() = runTest(dispatcher.scheduler) {
        val env = Env()
        val vm = env.vm()
        vm.uiState.test {
            latest()
            env.catalog.result = DataResult.Success(listOf(thread(9)))
            vm.load(forceRefresh = true)
            val content = (latest() as UiState.Success).data
            assertEquals(listOf(9L), content.threads.map { it.no })
            assertEquals(true, env.catalog.forceFlags.last())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `retry after an error forces the fetch`() = runTest(dispatcher.scheduler) {
        val env = Env()
        env.catalog.result = DataResult.Failure(NetworkError.NotFound)
        val vm = env.vm()
        vm.uiState.test {
            latest()
            env.catalog.result = DataResult.Success(listOf(thread(9)))
            vm.retry()
            val content = (latest() as UiState.Success).data
            assertEquals(listOf(9L), content.threads.map { it.no })
            assertEquals(true, env.catalog.forceFlags.last())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `hidden threads are filtered and hide-undo round-trips`() = runTest(dispatcher.scheduler) {
        val env = Env()
        val vm = env.vm()
        vm.uiState.test {
            latest()
            vm.onHideThread(2)
            val afterHide = (latest() as UiState.Success).data
            assertEquals(listOf(1L, 3L), afterHide.threads.map { it.no })
            vm.onUndoHide(2)
            val afterUndo = (latest() as UiState.Success).data
            assertEquals(listOf(1L, 2L, 3L), afterUndo.threads.map { it.no })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `hidden threads on other boards do not filter this catalog`() =
        runTest(dispatcher.scheduler) {
            val env = Env()
            env.hidden.state.value = listOf(HiddenThread("a", 2))
            env.vm().uiState.test {
                val content = (latest() as UiState.Success).data
                assertEquals(listOf(1L, 2L, 3L), content.threads.map { it.no })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `search matches subject or excerpt case-insensitively`() = runTest(dispatcher.scheduler) {
        val env = Env()
        val vm = env.vm()
        vm.uiState.test {
            latest()
            vm.onSearchChange("ALPHA")
            val bySubject = (latest() as UiState.Success).data
            assertEquals(listOf(1L), bySubject.threads.map { it.no })
            vm.onSearchChange("excerpt 3")
            val byExcerpt = (latest() as UiState.Success).data
            assertEquals(listOf(3L), byExcerpt.threads.map { it.no })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `closing search drops the filter, not just the field`() = runTest(dispatcher.scheduler) {
        val env = Env()
        val vm = env.vm()
        vm.uiState.test {
            latest()
            vm.onOpenSearch()
            vm.onSearchChange("Alpha")
            assertEquals(listOf(1L), (latest() as UiState.Success).data.threads.map { it.no })
            vm.onCloseSearch()
            val closed = (latest() as UiState.Success).data
            assertEquals(null, closed.searchQuery)
            assertEquals(listOf(1L, 2L, 3L), closed.threads.map { it.no })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `initial search from navigation is applied immediately`() = runTest(dispatcher.scheduler) {
        val env = Env()
        env.vm(initialSearch = "Alpha").uiState.test {
            val content = (latest() as UiState.Success).data
            assertEquals("Alpha", content.searchQuery)
            assertEquals(listOf(1L), content.threads.map { it.no })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `cycle layout advances the setting and wraps around`() = runTest(dispatcher.scheduler) {
        val env = Env()
        val vm = env.vm()
        vm.uiState.test {
            latest()
            vm.onCycleLayout()
            assertEquals(CatalogLayout.COMPACT, ((latest() as UiState.Success).data).layout)
            vm.onCycleLayout()
            vm.onCycleLayout()
            assertEquals(CatalogLayout.COMFORTABLE, ((latest() as UiState.Success).data).layout)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `a hide filter drops the thread and counts it`() = runTest(dispatcher.scheduler) {
        val env = Env()
        env.settings.state.value = Settings(filters = listOf(
            Filter(id = "1", pattern = "ALPHA", field = FilterField.SUBJECT, action = FilterAction.HIDE),
        ))
        env.vm().uiState.test {
            val content = (latest() as UiState.Success).data
            assertEquals(listOf(2L, 3L), content.threads.map { it.no })
            assertEquals(1, content.filteredCount)
            assertEquals(emptyMap<Long, Filter>(), content.stubs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `a stub filter keeps the thread but marks it`() = runTest(dispatcher.scheduler) {
        val env = Env()
        val stub = Filter(id = "1", pattern = "excerpt 2", action = FilterAction.STUB)
        env.settings.state.value = Settings(filters = listOf(stub))
        env.vm().uiState.test {
            val content = (latest() as UiState.Success).data
            assertEquals(listOf(1L, 2L, 3L), content.threads.map { it.no })
            assertEquals(mapOf(2L to stub), content.stubs)
            assertEquals(1, content.filteredCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `filters scoped to another board leave this catalog alone`() = runTest(dispatcher.scheduler) {
        val env = Env()
        env.settings.state.value = Settings(filters = listOf(
            Filter(id = "1", pattern = "excerpt", boards = setOf("a")),
        ))
        env.vm().uiState.test {
            val content = (latest() as UiState.Success).data
            assertEquals(listOf(1L, 2L, 3L), content.threads.map { it.no })
            assertEquals(0, content.filteredCount)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

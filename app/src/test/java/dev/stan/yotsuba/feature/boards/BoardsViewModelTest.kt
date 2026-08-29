package dev.stan.yotsuba.feature.boards

import app.cash.turbine.test
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.core.util.UiState
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoardsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun board(
        code: String,
        title: String = code.uppercase(),
        category: BoardCategory = BoardCategory.INTERESTS,
        worksafe: Boolean = true,
    ) = Board(
        code = code, title = title, description = "", worksafe = worksafe, category = category,
        userIds = false, countryFlags = false, boardFlags = false, spoilers = false,
        webmAudio = false, codeTags = false, mathTags = false, sjisTags = false, textOnly = false,
    )

    private class FakeBoardRepository(var result: DataResult<List<Board>>) : BoardRepository {
        override suspend fun boards(forceRefresh: Boolean) = result
        override suspend fun board(code: String) =
            (result as? DataResult.Success)?.value?.firstOrNull { it.code == code }
    }

    private class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
        val state = MutableStateFlow(initial)
        override val settings: Flow<Settings> = state
        override suspend fun update(transform: (Settings) -> Settings) {
            state.value = transform(state.value)
        }
    }

    private fun vm(
        boards: FakeBoardRepository,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ) = BoardsViewModel(boards, settings)

    private suspend fun app.cash.turbine.TurbineTestContext<UiState<BoardsContent>>.latest(): UiState<BoardsContent> {
        dispatcher.scheduler.advanceUntilIdle()
        return expectMostRecentItem()
    }

    @Test fun `successful load groups boards by category in declaration order`() =
        runTest(dispatcher.scheduler) {
            val repo = FakeBoardRepository(DataResult.Success(listOf(
                board("g", category = BoardCategory.INTERESTS),
                board("a", category = BoardCategory.JAPANESE_CULTURE),
                board("v", category = BoardCategory.VIDEO_GAMES),
                board("m", category = BoardCategory.JAPANESE_CULTURE),
            )))
            vm(repo).uiState.test {
                val state = latest()
                val content = (state as UiState.Success).data
                assertEquals(
                    listOf(BoardCategory.JAPANESE_CULTURE, BoardCategory.VIDEO_GAMES, BoardCategory.INTERESTS),
                    content.sections.map { it.category },
                )
                assertEquals(listOf("a", "m"), content.sections[0].boards.map { it.board.code })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `failed load surfaces the network error`() = runTest(dispatcher.scheduler) {
        val repo = FakeBoardRepository(DataResult.Failure(NetworkError.Offline))
        vm(repo).uiState.test {
            val state = latest()
            assertEquals(NetworkError.Offline, (state as UiState.Error).error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `search matches board code or title case-insensitively`() =
        runTest(dispatcher.scheduler) {
            val repo = FakeBoardRepository(DataResult.Success(listOf(
                board("g", title = "Technology"),
                board("v", title = "Video Games"),
                board("tv", title = "Television & Film"),
            )))
            val vm = vm(repo)
            vm.uiState.test {
                latest()
                vm.onSearchChange("TECH")
                val byTitle = (latest() as UiState.Success).data
                assertEquals(listOf("g"), byTitle.sections.flatMap { it.boards }.map { it.board.code })
                vm.onSearchChange("tv")
                val byCode = (latest() as UiState.Success).data
                assertEquals(listOf("tv"), byCode.sections.flatMap { it.boards }.map { it.board.code })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `hidden boards are filtered out unless edit mode is on`() =
        runTest(dispatcher.scheduler) {
            val repo = FakeBoardRepository(DataResult.Success(listOf(board("g"), board("v"))))
            val vm = vm(repo, FakeSettingsRepository(Settings(hiddenBoards = setOf("v"))))
            vm.uiState.test {
                val visible = (latest() as UiState.Success).data
                assertEquals(listOf("g"), visible.sections.flatMap { it.boards }.map { it.board.code })
                vm.onToggleEditMode()
                val editing = (latest() as UiState.Success).data
                assertEquals(listOf("g", "v"), editing.sections.flatMap { it.boards }.map { it.board.code })
                assertTrue(editing.editMode)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `favourites come from settings and toggle round-trips`() =
        runTest(dispatcher.scheduler) {
            val repo = FakeBoardRepository(DataResult.Success(listOf(board("g"), board("v"))))
            val settings = FakeSettingsRepository(Settings(favouriteBoards = setOf("g")))
            val vm = vm(repo, settings)
            vm.uiState.test {
                val content = (latest() as UiState.Success).data
                assertEquals(listOf("g"), content.favourites.map { it.code })
                vm.onToggleFavourite("v")
                val added = (latest() as UiState.Success).data
                assertEquals(listOf("g", "v"), added.favourites.map { it.code })
                assertTrue(added.sections.flatMap { it.boards }.all { it.favourite })
                vm.onToggleFavourite("g")
                val removed = (latest() as UiState.Success).data
                assertEquals(listOf("v"), removed.favourites.map { it.code })
                assertEquals(
                    listOf(false, true),
                    removed.sections.flatMap { it.boards }.map { it.favourite },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `category header tri-state reflects hidden boards within the category`() =
        runTest(dispatcher.scheduler) {
            val repo = FakeBoardRepository(DataResult.Success(listOf(
                board("a", category = BoardCategory.JAPANESE_CULTURE),
                board("m", category = BoardCategory.JAPANESE_CULTURE),
            )))
            val settings = FakeSettingsRepository(Settings(hiddenBoards = setOf("m")))
            val vm = vm(repo, settings)
            vm.onToggleEditMode()
            vm.uiState.test {
                val mixed = (latest() as UiState.Success).data
                assertEquals(null, mixed.sections.single().allVisible)
                settings.state.value = Settings()
                val all = (latest() as UiState.Success).data
                assertEquals(true, all.sections.single().allVisible)
                settings.state.value = Settings(hiddenBoards = setOf("a", "m"))
                val none = (latest() as UiState.Success).data
                assertEquals(false, none.sections.single().allVisible)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `toggling a hidden category shows all of its boards again`() =
        runTest(dispatcher.scheduler) {
            val repo = FakeBoardRepository(DataResult.Success(listOf(
                board("a", category = BoardCategory.JAPANESE_CULTURE),
                board("m", category = BoardCategory.JAPANESE_CULTURE),
            )))
            val settings = FakeSettingsRepository(
                Settings(hiddenCategories = setOf(BoardCategory.JAPANESE_CULTURE.name), hiddenBoards = setOf("a"))
            )
            val vm = vm(repo, settings)
            vm.uiState.test {
                latest()
                vm.onToggleCategoryVisible(BoardCategory.JAPANESE_CULTURE)
                dispatcher.scheduler.advanceUntilIdle()
                assertEquals(emptySet<String>(), settings.state.value.hiddenCategories)
                assertEquals(emptySet<String>(), settings.state.value.hiddenBoards)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

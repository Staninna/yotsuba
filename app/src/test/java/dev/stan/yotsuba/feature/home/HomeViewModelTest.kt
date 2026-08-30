package dev.stan.yotsuba.feature.home

import app.cash.turbine.test
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.fake.FakeSettings
import dev.stan.yotsuba.fake.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test fun `pages follow the favourites in their saved order`() = runTest(dispatcher.scheduler) {
        val settings = FakeSettings(Settings(favouriteBoards = linkedSetOf("g", "a")))
        val vm = HomeViewModel(settings)
        vm.boards.test {
            assertEquals(null, awaitItem())
            assertEquals(listOf("g", "a"), awaitItem())
            settings.update { it.copy(favouriteBoards = it.favouriteBoards + "v") }
            assertEquals(listOf("g", "a", "v"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `removing a favourite and undoing it keeps the old position`() = runTest(dispatcher.scheduler) {
        val settings = FakeSettings(Settings(favouriteBoards = linkedSetOf("g", "a", "v")))
        val vm = HomeViewModel(settings)
        val undo = vm.removeFavourite("a")
        advanceUntilIdle()
        assertEquals(listOf("g", "v"), settings.state.value.favouriteBoards.toList())
        undo()
        advanceUntilIdle()
        assertEquals(listOf("g", "a", "v"), settings.state.value.favouriteBoards.toList())
    }

    @Test fun `undoing before the removal has landed still restores the board`() = runTest(dispatcher.scheduler) {
        val settings = SlowFirstWrite(Settings(favouriteBoards = linkedSetOf("g", "a", "v")))
        val vm = HomeViewModel(settings)
        val undo = vm.removeFavourite("a")
        undo()
        advanceUntilIdle()
        assertEquals(listOf("g", "a", "v"), settings.current.favouriteBoards.toList())
    }

    @Test fun `reordering moves the board and persists the new order`() = runTest(dispatcher.scheduler) {
        val settings = OrderedSettings(Settings(favouriteBoards = linkedSetOf("g", "a", "v", "k")))
        val vm = HomeViewModel(settings)
        vm.reorder(from = 0, to = 2)
        advanceUntilIdle()
        assertEquals(listOf("a", "v", "g", "k"), settings.current.favouriteBoards.toList())
        vm.reorder(from = 3, to = 0)
        advanceUntilIdle()
        assertEquals(listOf("k", "a", "v", "g"), settings.current.favouriteBoards.toList())
        assertEquals(2, settings.writes)
    }

    @Test fun `reordering out of range leaves the favourites alone`() = runTest(dispatcher.scheduler) {
        val settings = OrderedSettings(Settings(favouriteBoards = linkedSetOf("g", "a")))
        val vm = HomeViewModel(settings)
        vm.reorder(from = 0, to = 5)
        vm.reorder(from = 1, to = 1)
        advanceUntilIdle()
        assertEquals(listOf("g", "a"), settings.current.favouriteBoards.toList())
    }

    /** A store whose first write takes longer than the next, like a DataStore write racing a tap. */
    private class SlowFirstWrite(var current: Settings) : SettingsRepository {
        private var writes = 0
        override val settings: Flow<Settings> = flowOf(current)
        override suspend fun update(transform: (Settings) -> Settings) {
            delay(if (writes++ == 0) 100 else 1)
            current = transform(current)
        }
    }

    /**
     * [FakeSettings] sits on a `MutableStateFlow`, which drops a value equal to the last one,
     * and two sets with the same boards in a different order are equal. The real store writes
     * a JSON string, so a reorder does reach disk; this double keeps the order visible.
     */
    private class OrderedSettings(var current: Settings) : SettingsRepository {
        var writes = 0
        private val emissions = MutableSharedFlow<Settings>(replay = 1).also { it.tryEmit(current) }
        override val settings: Flow<Settings> = emissions
        override suspend fun update(transform: (Settings) -> Settings) {
            current = transform(current)
            writes++
            emissions.emit(current)
        }
    }
}

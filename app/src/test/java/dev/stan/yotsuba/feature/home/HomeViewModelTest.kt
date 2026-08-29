package dev.stan.yotsuba.feature.home

import app.cash.turbine.test
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.fake.FakeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

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
}

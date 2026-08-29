package dev.stan.yotsuba.feature

import app.cash.turbine.test
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.feature.history.HistoryBucket
import dev.stan.yotsuba.feature.history.HistoryUiState
import dev.stan.yotsuba.feature.history.HistoryViewModel
import java.time.LocalDate
import java.time.ZoneId
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val startOfToday: Long =
        LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()

    private fun entry(no: Long, viewedAt: Long, maxRead: Long? = null) = HistoryEntry(
        board = "g", threadNo = no, subject = null, opExcerpt = "e$no",
        thumbnailUrl = null, viewedAt = viewedAt, lastScrollPostNo = null,
        maxReadPostNo = maxRead,
    )

    private class FakeHistoryRepository(initial: List<HistoryEntry>) : HistoryRepository {
        val state = MutableStateFlow(initial)
        var cleared = false
        override val history: Flow<List<HistoryEntry>> = state
        override suspend fun record(entry: HistoryEntry) {
            // Mirrors the DAO: a visit never carries the read mark with it.
            state.value = listOf(entry.copy(maxReadPostNo = null)) + state.value.filterNot {
                it.board == entry.board && it.threadNo == entry.threadNo
            }
        }
        override suspend fun restore(entry: HistoryEntry) {
            if (state.value.none { it.board == entry.board && it.threadNo == entry.threadNo }) {
                state.value = state.value + entry
            }
        }
        override suspend fun updateScrollPosition(board: String, threadNo: Long, postNo: Long) {}
        override suspend fun lastScrollPosition(board: String, threadNo: Long): Long? = null
        override suspend fun updateReadUpTo(board: String, threadNo: Long, postNo: Long) {}
        override suspend fun readUpTo(board: String, threadNo: Long): Long? = null
        override suspend fun remove(board: String, threadNo: Long) {
            state.value = state.value.filterNot { it.board == board && it.threadNo == threadNo }
        }
        override suspend fun clearAll() {
            cleared = true
            state.value = emptyList()
        }
        override suspend fun trim(retainAfterMs: Long) {}
    }

    private class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
        val state = MutableStateFlow(initial)
        override val settings: Flow<Settings> = state
        override suspend fun update(transform: (Settings) -> Settings) {
            state.value = transform(state.value)
        }
    }

    private suspend fun app.cash.turbine.TurbineTestContext<HistoryUiState>.latest(): HistoryUiState {
        dispatcher.scheduler.advanceUntilIdle()
        return expectMostRecentItem()
    }

    @Test fun `entries are grouped into date buckets`() = runTest(dispatcher.scheduler) {
        val repo = FakeHistoryRepository(listOf(
            entry(1, startOfToday + 1_000),
            entry(2, startOfToday - 3_600_000),          // yesterday
            entry(3, startOfToday - 3 * 86_400_000),     // this week
            entry(4, startOfToday - 30L * 86_400_000),   // older
        ))
        HistoryViewModel(repo, FakeSettingsRepository()).uiState.test {
            val state = latest()
            assertTrue(state.loaded)
            val byBucket = state.groups.toMap()
            assertEquals(listOf(1L), byBucket[HistoryBucket.TODAY]?.map { it.threadNo })
            assertEquals(listOf(2L), byBucket[HistoryBucket.YESTERDAY]?.map { it.threadNo })
            assertEquals(listOf(3L), byBucket[HistoryBucket.THIS_WEEK]?.map { it.threadNo })
            assertEquals(listOf(4L), byBucket[HistoryBucket.OLDER]?.map { it.threadNo })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `recording setting is passed through`() = runTest(dispatcher.scheduler) {
        val repo = FakeHistoryRepository(emptyList())
        val settings = FakeSettingsRepository(Settings(recordHistory = false))
        HistoryViewModel(repo, settings).uiState.test {
            assertFalse(latest().recordingEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `remove and undo restore the entry with its read mark`() = runTest(dispatcher.scheduler) {
        val e = entry(1, startOfToday + 1_000, maxRead = 42)
        val repo = FakeHistoryRepository(listOf(e))
        val vm = HistoryViewModel(repo, FakeSettingsRepository())
        vm.uiState.test {
            latest()
            vm.onRemove(e)
            assertTrue(latest().groups.isEmpty())
            vm.onUndoRemove(e)
            val restored = latest().groups.single().second.single()
            assertEquals(1L, restored.threadNo)
            assertEquals(42L, restored.maxReadPostNo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `clear all empties the repository`() = runTest(dispatcher.scheduler) {
        val repo = FakeHistoryRepository(listOf(entry(1, startOfToday), entry(2, startOfToday)))
        val vm = HistoryViewModel(repo, FakeSettingsRepository())
        vm.uiState.test {
            latest()
            vm.onClearAll()
            assertTrue(latest().groups.isEmpty())
            assertTrue(repo.cleared)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

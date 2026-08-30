package dev.stan.yotsuba.feature

import app.cash.turbine.test
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.fake.FakeSettings
import dev.stan.yotsuba.feature.history.HistoryBucket
import dev.stan.yotsuba.feature.history.HistoryUiState
import dev.stan.yotsuba.feature.history.HistoryViewModel
import dev.stan.yotsuba.feature.history.bucketOf
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

    private val zone: ZoneId = ZoneOffset.UTC

    /** A fixed "now": 2026-03-10 at 15:00 UTC. */
    private val nowMs: Long = LocalDate.of(2026, 3, 10).atTime(15, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val startOfToday: Long = LocalDate.of(2026, 3, 10).atStartOfDay(zone).toInstant().toEpochMilli()

    /** A clock the test moves by hand. */
    private class ManualClock(var now: Long, private val zone: ZoneId) : Clock() {
        override fun getZone() = zone
        override fun withZone(zone: ZoneId) = ManualClock(now, zone)
        override fun instant(): Instant = Instant.ofEpochMilli(now)
    }

    private val clock = ManualClock(nowMs, zone)
    private val ticks = MutableSharedFlow<Unit>()

    private fun vm(repo: HistoryRepository, settings: SettingsRepository = FakeSettings()) =
        HistoryViewModel(repo, settings, clock, ticks)

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
        vm(repo).uiState.test {
            val state = latest()
            assertTrue(state.loaded)
            val byBucket = state.groups.associate { it.bucket to it.entries }
            assertEquals(listOf(1L), byBucket[HistoryBucket.TODAY]?.map { it.threadNo })
            assertEquals(listOf(2L), byBucket[HistoryBucket.YESTERDAY]?.map { it.threadNo })
            assertEquals(listOf(3L), byBucket[HistoryBucket.THIS_WEEK]?.map { it.threadNo })
            assertEquals(listOf(4L), byBucket[HistoryBucket.OLDER]?.map { it.threadNo })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `bucketOf draws its lines at local midnight`() {
        assertEquals(HistoryBucket.TODAY, bucketOf(startOfToday, startOfToday))
        assertEquals(HistoryBucket.YESTERDAY, bucketOf(startOfToday - 1, startOfToday))
        assertEquals(HistoryBucket.YESTERDAY, bucketOf(startOfToday - 86_400_000, startOfToday))
        assertEquals(HistoryBucket.THIS_WEEK, bucketOf(startOfToday - 86_400_001, startOfToday))
        assertEquals(HistoryBucket.THIS_WEEK, bucketOf(startOfToday - 6 * 86_400_000L, startOfToday))
        assertEquals(HistoryBucket.OLDER, bucketOf(startOfToday - 6 * 86_400_000L - 1, startOfToday))
    }

    @Test fun `groups re-bucket when the date rolls over`() = runTest(dispatcher.scheduler) {
        val repo = FakeHistoryRepository(listOf(entry(1, startOfToday + 1_000)))
        vm(repo).uiState.test {
            assertEquals(HistoryBucket.TODAY, latest().groups.single().bucket)
            clock.now = startOfToday + 86_400_000 + 60_000 // one minute past midnight
            ticks.emit(Unit)
            assertEquals(HistoryBucket.YESTERDAY, latest().groups.single().bucket)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `recording setting is passed through`() = runTest(dispatcher.scheduler) {
        val repo = FakeHistoryRepository(emptyList())
        val settings = FakeSettings(Settings(recordHistory = false))
        vm(repo, settings).uiState.test {
            assertFalse(latest().recordingEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `remove and undo restore the entry with its read mark`() = runTest(dispatcher.scheduler) {
        val e = entry(1, startOfToday + 1_000, maxRead = 42)
        val repo = FakeHistoryRepository(listOf(e))
        val vm = vm(repo)
        vm.uiState.test {
            latest()
            vm.onRemove(e)
            assertTrue(latest().groups.isEmpty())
            vm.onUndoRemove(e)
            val restored = latest().groups.single().entries.single()
            assertEquals(1L, restored.threadNo)
            assertEquals(42L, restored.maxReadPostNo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `search filters by title and board but keeps the total count`() = runTest(dispatcher.scheduler) {
        val repo = FakeHistoryRepository(listOf(
            entry(1, startOfToday).copy(subject = "Rust thread"),
            entry(2, startOfToday).copy(board = "a", subject = "Anime"),
        ))
        val vm = vm(repo)
        vm.uiState.test {
            latest()
            vm.onQueryChange("rust")
            var state = latest()
            assertEquals(listOf(1L), state.groups.flatMap { it.entries }.map { it.threadNo })
            assertEquals(2, state.totalCount)
            vm.onQueryChange("/a/")
            state = latest()
            assertEquals(listOf(2L), state.groups.flatMap { it.entries }.map { it.threadNo })
            vm.onQueryChange("zzz")
            assertTrue(latest().groups.isEmpty())
            vm.onQueryChange("/")
            assertTrue(latest().groups.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `clear all empties the repository`() = runTest(dispatcher.scheduler) {
        val repo = FakeHistoryRepository(listOf(entry(1, startOfToday), entry(2, startOfToday)))
        val vm = vm(repo)
        vm.uiState.test {
            latest()
            vm.onClearAll()
            assertTrue(latest().groups.isEmpty())
            assertTrue(repo.cleared)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

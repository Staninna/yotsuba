package dev.stan.yotsuba.feature

import androidx.test.core.app.ApplicationProvider
import dev.stan.yotsuba.core.update.GithubReleases
import dev.stan.yotsuba.core.update.Updater
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.repository.BackupInfo
import dev.stan.yotsuba.domain.repository.BackupRepository
import dev.stan.yotsuba.domain.repository.BackupResult
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRefreshSummary
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.MaintenanceRepository
import dev.stan.yotsuba.fake.FakeSettings
import dev.stan.yotsuba.feature.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric for the same reason as SettingsViewModelTest: [Updater] wants a Context. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class SettingsBackupViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeBackup(
        val fresh: Boolean,
        val info: BackupInfo?,
    ) : BackupRepository {
        var exports = 0
        var imports = 0
        var probes = 0
        override suspend fun export(): BackupResult { exports++; return BackupResult.Exported(1L) }
        override suspend fun import(): BackupResult { imports++; return BackupResult.Imported(2, 1) }
        override suspend fun available(): BackupInfo? = info
        override suspend fun isFreshInstall(): Boolean { probes++; return fresh }
    }

    private object NoHistory : HistoryRepository {
        override val history: Flow<List<HistoryEntry>> = flowOf(emptyList())
        override suspend fun record(entry: HistoryEntry) {}
        override suspend fun updateScrollPosition(board: String, threadNo: Long, postNo: Long) {}
        override suspend fun lastScrollPosition(board: String, threadNo: Long): Long? = null
        override suspend fun updateReadUpTo(board: String, threadNo: Long, postNo: Long) {}
        override suspend fun readUpTo(board: String, threadNo: Long): Long? = null
        override suspend fun remove(board: String, threadNo: Long) {}
        override suspend fun restore(entry: HistoryEntry) = record(entry)
        override suspend fun clearAll() {}
        override suspend fun trim(retainAfterMs: Long) {}
    }

    private object NoBookmarks : BookmarkRepository {
        override val bookmarks: Flow<List<Bookmark>> = flowOf(emptyList())
        override suspend fun add(bookmark: Bookmark) {}
        override suspend fun remove(board: String, threadNo: Long) {}
        override fun isBookmarked(board: String, threadNo: Long) = flowOf(false)
        override suspend fun markSeen(board: String, threadNo: Long, postNo: Long) {}
        override suspend fun refreshAll(onProgress: (Int, Int) -> Unit) = BookmarkRefreshSummary()
        override suspend fun setPinned(board: String, threadNo: Long, pinned: Boolean) {}
        override suspend fun removeDead() {}
        override suspend fun clearAll() {}
    }

    private object NoHidden : HiddenThreadsRepository {
        override val all: Flow<List<HiddenThread>> = flowOf(emptyList())
        override fun forBoard(board: String) = flowOf(emptyList<HiddenThread>())
        override suspend fun hide(board: String, threadNo: Long) {}
        override suspend fun unhide(board: String, threadNo: Long) {}
    }

    private object NoBoards : BoardRepository {
        override suspend fun boards(forceRefresh: Boolean) = DataResult.Success(emptyList<Board>())
        override suspend fun board(code: String): Board? = null
    }

    private object NoMaintenance : MaintenanceRepository {
        override suspend fun clearCaches() {}
    }

    private fun vm(backup: BackupRepository) = SettingsViewModel(
        settingsRepository = FakeSettings(),
        historyRepository = NoHistory,
        bookmarkRepository = NoBookmarks,
        hiddenThreadsRepository = NoHidden,
        boardRepository = NoBoards,
        maintenanceRepository = NoMaintenance,
        updater = Updater(ApplicationProvider.getApplicationContext(), GithubReleases()),
        backupRepository = backup,
    )

    @Test
    fun `fresh install with a backup offers a restore`() = runTest {
        val info = BackupInfo(exportedAt = 123L)
        val vm = vm(FakeBackup(fresh = true, info = info))
        vm.onStorageSectionShown()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(info, vm.restoreAvailable.value)
    }

    @Test
    fun `an install with data never offers a restore`() = runTest {
        val vm = vm(FakeBackup(fresh = false, info = BackupInfo(123L)))
        vm.onStorageSectionShown()
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.restoreAvailable.value)
    }

    @Test
    fun `dismiss hides the prompt and restore imports then hides it`() = runTest {
        val backup = FakeBackup(fresh = true, info = BackupInfo(123L))
        val vm = vm(backup)
        vm.onStorageSectionShown()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onDismissRestore()
        assertNull(vm.restoreAvailable.value)
        vm.onStorageSectionShown()
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.restoreAvailable.value)

        val again = vm(backup)
        again.onStorageSectionShown()
        dispatcher.scheduler.advanceUntilIdle()
        again.onImportBackup()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, backup.imports)
        assertNull(again.restoreAvailable.value)
        assertEquals(BackupResult.Imported(2, 1), again.backupResult.value)
        again.onBackupResultShown()
        assertNull(again.backupResult.value)
    }

    @Test
    fun `only the storage section probes for a backup`() = runTest {
        val backup = FakeBackup(fresh = true, info = BackupInfo(123L))
        val vm = vm(backup)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, backup.probes)
        assertNull(vm.restoreAvailable.value)
    }

    @Test
    fun `export now reports its result`() = runTest {
        val backup = FakeBackup(fresh = false, info = null)
        val vm = vm(backup)
        vm.onExportBackup()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, backup.exports)
        assertEquals(BackupResult.Exported(1L), vm.backupResult.value)
    }
}

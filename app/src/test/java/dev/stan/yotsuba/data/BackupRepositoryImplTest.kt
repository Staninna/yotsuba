package dev.stan.yotsuba.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.stan.yotsuba.core.backup.BackupFile
import dev.stan.yotsuba.core.backup.StorageAccessCheck
import dev.stan.yotsuba.data.repository.BackupRepositoryImpl
import dev.stan.yotsuba.data.repository.VaultStore
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThemeMode
import dev.stan.yotsuba.domain.repository.BackupResult
import dev.stan.yotsuba.domain.repository.BookmarkRefreshSummary
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import dev.stan.yotsuba.fake.FakeSettings
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupRepositoryImplTest {

    @get:Rule val folder = TemporaryFolder()

    private class FakeBookmarks(initial: List<Bookmark> = emptyList()) : BookmarkRepository {
        val state = MutableStateFlow(initial)
        override val bookmarks: Flow<List<Bookmark>> = state
        override suspend fun add(bookmark: Bookmark) {
            state.value = state.value.filterNot { it.board == bookmark.board && it.threadNo == bookmark.threadNo } + bookmark
        }
        override suspend fun remove(board: String, threadNo: Long) {
            state.value = state.value.filterNot { it.board == board && it.threadNo == threadNo }
        }
        override fun isBookmarked(board: String, threadNo: Long) = flowOf(false)
        override suspend fun markSeen(board: String, threadNo: Long, lastSeenPostNo: Long) {}
        override suspend fun refreshAll(onProgress: (Int, Int) -> Unit) = BookmarkRefreshSummary()
        override suspend fun setPinned(board: String, threadNo: Long, pinned: Boolean) {}
        override suspend fun removeDead() {}
        override suspend fun clearAll() { state.value = emptyList() }
    }

    private class FakeHidden(initial: List<HiddenThread> = emptyList()) : HiddenThreadsRepository {
        val state = MutableStateFlow(initial)
        override val all: Flow<List<HiddenThread>> = state
        override fun forBoard(board: String) = state.map { l -> l.filter { it.board == board } }
        override suspend fun hide(board: String, threadNo: Long) {
            state.value = state.value + HiddenThread(board, threadNo)
        }
        override suspend fun unhide(board: String, threadNo: Long) {
            state.value = state.value.filterNot { it.board == board && it.threadNo == threadNo }
        }
    }

    private class FakePreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            state.value = transform(state.value)
            return state.value
        }
    }

    @Suppress("DEPRECATION")
    private fun bookmark(board: String, no: Long, readUpTo: Long? = null, pinned: Boolean = false) = Bookmark(
        board = board, threadNo = no, subject = "s$no", opExcerpt = "", thumbnailUrl = null,
        replyCount = 3, imageCount = 1, bookmarkedAt = 1_000L, lastCheckedAt = null,
        lastSeenPostNo = null, state = BookmarkState.ALIVE, readUpTo = readUpTo, pinned = pinned,
    )

    private class Env(
        root: File,
        val bookmarks: FakeBookmarks = FakeBookmarks(),
        val hidden: FakeHidden = FakeHidden(),
        val settings: FakeSettings = FakeSettings(),
        val prefs: FakePreferences = FakePreferences(),
        scope: CoroutineScope = CoroutineScope(Job()),
        access: Boolean = true,
    ) {
        val repo = BackupRepositoryImpl(
            VaultStore(root), bookmarks, hidden, settings, prefs, StorageAccessCheck { access }, scope,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @Test
    fun `export then import restores everything on an empty install`() = runTest {
        val root = folder.newFolder()
        val source = Env(
            root,
            bookmarks = FakeBookmarks(listOf(bookmark("g", 1, readUpTo = 10, pinned = true), bookmark("a", 2))),
            hidden = FakeHidden(listOf(HiddenThread("g", 5))),
            settings = FakeSettings(Settings(themeMode = ThemeMode.DARK, favouriteBoards = setOf("g"))),
        )
        assertTrue(source.repo.export() is BackupResult.Exported)
        assertTrue(File(root, BackupFile.FILE_NAME).isFile)

        val target = Env(root)
        val result = target.repo.import()
        assertEquals(BackupResult.Imported(bookmarks = 2, hiddenThreads = 1), result)
        assertEquals(source.bookmarks.state.value.toSet(), target.bookmarks.state.value.toSet())
        assertEquals(listOf(HiddenThread("g", 5)), target.hidden.state.value)
        assertEquals(ThemeMode.DARK, target.settings.state.value.themeMode)
        assertEquals(setOf("g"), target.settings.state.value.favouriteBoards)
    }

    @Test
    fun `import keeps the higher read mark and unions hidden threads`() = runTest {
        val root = folder.newFolder()
        Env(
            root,
            bookmarks = FakeBookmarks(listOf(bookmark("g", 1, readUpTo = 50), bookmark("g", 2, readUpTo = 5, pinned = true))),
            hidden = FakeHidden(listOf(HiddenThread("g", 5))),
        ).repo.export()

        val target = Env(
            root,
            bookmarks = FakeBookmarks(listOf(bookmark("g", 1, readUpTo = 80), bookmark("g", 2, readUpTo = 2), bookmark("v", 9))),
            hidden = FakeHidden(listOf(HiddenThread("g", 5), HiddenThread("v", 7))),
        )
        target.repo.import()

        val byNo = target.bookmarks.state.value.associateBy { it.threadNo }
        assertEquals(80L, byNo.getValue(1).readUpTo)
        assertEquals(5L, byNo.getValue(2).readUpTo)
        assertTrue(byNo.getValue(2).pinned)
        assertNotNull(byNo[9])
        assertEquals(setOf(HiddenThread("g", 5), HiddenThread("v", 7)), target.hidden.state.value.toSet())
    }

    @Test
    fun `unknown keys in the file are tolerated`() = runTest {
        val root = folder.newFolder()
        File(root, BackupFile.FILE_NAME).writeText(
            """
            {"version": 7, "exportedAt": 42, "futureField": {"x": 1},
             "settings": {"themeMode": "LIGHT", "somethingNew": true},
             "bookmarks": [{"board": "g", "threadNo": 3, "extra": "y"}],
             "hiddenThreads": [{"board": "g", "threadNo": 4, "why": "spam"}]}
            """.trimIndent(),
        )
        val target = Env(root)
        assertEquals(42L, target.repo.available()?.exportedAt)
        assertEquals(BackupResult.Imported(1, 1), target.repo.import())
        assertEquals(ThemeMode.LIGHT, target.settings.state.value.themeMode)
        assertEquals(3L, target.bookmarks.state.value.single().threadNo)
    }

    @Test
    fun `import without a file reports NoBackup and a corrupt file reports Failed`() = runTest {
        val root = folder.newFolder()
        val target = Env(root)
        assertEquals(BackupResult.NoBackup, target.repo.import())
        assertNull(target.repo.available())
        File(root, BackupFile.FILE_NAME).writeText("{not json")
        assertTrue(target.repo.import() is BackupResult.Failed)
        assertNull(target.repo.available())
    }

    @Test
    fun `fresh install means no bookmarks and no settings blob`() = runTest {
        val root = folder.newFolder()
        assertTrue(Env(root).repo.isFreshInstall())
        assertFalse(Env(root, bookmarks = FakeBookmarks(listOf(bookmark("g", 1)))).repo.isFreshInstall())
        val written = FakePreferences(mutablePreferencesOf(stringPreferencesKey("settings") to "{}"))
        assertFalse(Env(root, prefs = written).repo.isFreshInstall())
        assertTrue(Env(root).settings.settings.first() == Settings())
    }

    @Test
    fun `a change re-exports after the debounce, the initial state does not`() = runTest {
        val root = folder.newFolder()
        val env = Env(root, scope = backgroundScope)
        val file = File(root, BackupFile.FILE_NAME)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertFalse(file.exists())

        env.bookmarks.add(bookmark("g", 1))
        advanceTimeBy(1_000)
        runCurrent()
        assertFalse(file.exists())
        env.hidden.hide("g", 2)
        advanceTimeBy(5_001)
        runCurrent()
        assertTrue(file.exists())
        assertTrue(file.readText().contains("\"threadNo\": 2"))
    }

    @Test
    fun `without storage access nothing is read or written`() = runTest {
        val root = folder.newFolder()
        File(root, BackupFile.FILE_NAME).writeText("{}")
        val env = Env(root, bookmarks = FakeBookmarks(listOf(bookmark("g", 1))), access = false, scope = backgroundScope)
        assertEquals(BackupResult.NoAccess, env.repo.export())
        assertEquals(BackupResult.NoAccess, env.repo.import())
        assertNull(env.repo.available())
        assertEquals("{}", File(root, BackupFile.FILE_NAME).readText())

        env.bookmarks.add(bookmark("g", 2))
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals("{}", File(root, BackupFile.FILE_NAME).readText())
    }
}

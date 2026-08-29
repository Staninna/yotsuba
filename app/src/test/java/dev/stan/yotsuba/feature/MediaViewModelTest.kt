package dev.stan.yotsuba.feature

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.stan.yotsuba.core.media.MediaByteSource
import dev.stan.yotsuba.core.network.NetworkMonitor
import dev.stan.yotsuba.core.text.PostSegment
import dev.stan.yotsuba.core.text.PostText
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.data.repository.MediaDownloadQueue
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory
import dev.stan.yotsuba.domain.model.MediaAutoplay
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.SeekStep
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import dev.stan.yotsuba.feature.media.MediaSessionStore
import dev.stan.yotsuba.feature.media.MediaUiState
import dev.stan.yotsuba.feature.media.MediaViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric only because [NetworkMonitor] and [MediaByteSource] are concrete classes
 * over Context; the repositories are faked at their interfaces. ExoPlayer never appears
 * here — the ViewModel itself is player-free.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class MediaViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeThreadRepository(
        var details: ThreadDetails,
        private val fails: Boolean = false,
    ) : ThreadRepository {
        override suspend fun thread(board: String, no: Long, forceRefresh: Boolean) =
            if (fails) DataResult.Failure(NetworkError.NotFound) else DataResult.Success(details)
    }

    private class FakeBoardRepository(var webmAudio: Boolean = false) : BoardRepository {
        override suspend fun boards(forceRefresh: Boolean) = DataResult.Success(emptyList<Board>())
        override suspend fun board(code: String) = Board(
            code = code, title = "Technology", description = "", worksafe = true,
            category = BoardCategory.INTERESTS, userIds = false, countryFlags = false,
            boardFlags = false, spoilers = false, webmAudio = webmAudio, codeTags = false,
            mathTags = false, sjisTags = false, textOnly = false,
        )
    }

    private class FakeSettingsRepository : SettingsRepository {
        val state = MutableStateFlow(Settings())
        override val settings: Flow<Settings> = state
        override suspend fun update(transform: (Settings) -> Settings) {
            state.value = transform(state.value)
        }
    }

    /** Saves resolve into [saved]; the first save completes [firstSave] for await-style asserts. */
    private class FakeVault : MediaVaultRepository {
        var access = true
        val saved = mutableListOf<Pair<MediaItem, VaultSaveContext>>()
        val firstSave = CompletableDeferred<VaultSaveContext>()
        val deleted = mutableListOf<String>()
        val urls = MutableStateFlow(emptySet<String>())
        val paths = MutableStateFlow(emptyMap<String, String>())
        override fun hasStorageAccess() = access
        override fun entries(): Flow<List<VaultEntry>> = flowOf(emptyList())
        override fun savedUrls(): Flow<Set<String>> = urls
        override fun savedPaths(): Flow<Map<String, String>> = paths
        override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? {
            saved += item to context
            firstSave.complete(context)
            return null
        }
        override suspend fun delete(url: String): VaultError? {
            deleted += url
            return null
        }
        var snapshot: ThreadDetails? = null
        override suspend fun savedThread(board: String, threadNo: Long): ThreadDetails? = snapshot
        override suspend fun rescan() {}
        override suspend fun migrateLegacyIfNeeded() {}
    }

    private fun media(postNo: Long) = MediaItem(
        postNo = postNo, filename = "img$postNo", ext = ".jpg", sizeBytes = 1,
        width = 10, height = 10, thumbnailUrl = "https://t/$postNo.jpg",
        fullUrl = "https://i/$postNo.jpg", spoiler = false,
    )

    private fun post(no: Long, withMedia: Boolean = true, quotes: List<Long> = emptyList()) =
        ThreadPost(
            board = "g", no = no, isOp = no == 100L, name = "Anonymous", tripcode = null,
            capcode = null, posterId = null, countryCode = null, countryName = null,
            timeSeconds = 0, subject = if (no == 100L) "OP subject" else null,
            body = PostText(listOf(PostSegment("body $no"))),
            media = if (withMedia) PostMedia.Present(media(no)) else null,
            quotedPostNos = quotes,
        )

    private class Env(
        posts: List<ThreadPost>,
        backlinks: Map<Long, List<Long>> = emptyMap(),
        val boards: FakeBoardRepository = FakeBoardRepository(),
        val settings: FakeSettingsRepository = FakeSettingsRepository(),
        val vault: FakeVault = FakeVault(),
        val sessionStore: MediaSessionStore = MediaSessionStore(),
        val server: MockWebServer? = null,
        threadFails: Boolean = false,
    ) {
        val threads = FakeThreadRepository(
            ThreadDetails("g", 100, posts, archived = false, closed = false, backlinks = backlinks),
            fails = threadFails,
        )
        val queue = MediaDownloadQueue(vault)
        val context: Context = ApplicationProvider.getApplicationContext()

        fun vm(initialPostNo: Long = 0) = MediaViewModel(
            board = "g", threadNo = 100, initialPostNo = initialPostNo,
            appContext = context,
            threadRepository = threads,
            boardRepository = boards,
            settingsRepository = settings,
            networkMonitor = NetworkMonitor(context),
            mediaVault = vault,
            downloadQueue = queue,
            byteSource = MediaByteSource(context, OkHttpClient()),
            sessionStore = sessionStore,
        )
    }

    private suspend fun app.cash.turbine.TurbineTestContext<MediaUiState>.latest(): MediaUiState {
        dispatcher.scheduler.advanceUntilIdle()
        return expectMostRecentItem()
    }

    @Test fun `a dead thread falls back to the conversation saved on disk`() =
        runTest(dispatcher.scheduler) {
            val env = Env(listOf(post(100)), threadFails = true)
            env.vault.snapshot = ThreadDetails(
                "g", 100,
                posts = listOf(post(100), post(101)),
                archived = false, closed = false,
                backlinks = mapOf(100L to listOf(101L)),
            )
            env.vm().uiState.test {
                val state = latest()
                assertEquals(setOf(100L, 101L), state.posts.keys)
                assertEquals(listOf(101L), state.graph.descendantsOf(100L).map { it.no })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `viewer behaviour mirrors the settings that drive the gestures`() =
        runTest(dispatcher.scheduler) {
            val env = Env(listOf(post(100)))
            env.settings.state.value = Settings(
                keepScreenOnWhileWatching = false,
                doubleTapSeekEnabled = true,
                seekStep = SeekStep.THIRTY,
                holdToSave = false,
            )
            env.vm().uiState.test {
                val behaviour = latest().behaviour
                assertEquals(false, behaviour.keepScreenOn)
                assertTrue(behaviour.doubleTapSeek)
                assertEquals(30, behaviour.seekStepSeconds)
                assertEquals(false, behaviour.holdToSave)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `items keep post order, skip media-less posts, and start at the requested post`() =
        runTest(dispatcher.scheduler) {
            val env = Env(listOf(post(100), post(101, withMedia = false), post(102), post(103)))
            env.vm(initialPostNo = 102).uiState.test {
                val state = latest()
                assertEquals(listOf(100L, 102L, 103L), state.items.map { it.postNo })
                assertEquals(1, state.initialIndex)
                assertTrue(state.loaded)
                assertEquals(setOf(100L, 101L, 102L, 103L), state.posts.keys)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `unknown initial post falls back to the first item`() = runTest(dispatcher.scheduler) {
        val env = Env(listOf(post(100), post(102)))
        env.vm(initialPostNo = 999).uiState.test {
            assertEquals(0, latest().initialIndex)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `the post graph walks backlinks transitively in post order`() =
        runTest(dispatcher.scheduler) {
            // 100 <- 102 <- 103, and 100 <- 104 directly.
            val env = Env(
                posts = listOf(post(100), post(102), post(103), post(104)),
                backlinks = mapOf(100L to listOf(104L, 102L), 102L to listOf(103L)),
            )
            env.vm().uiState.test {
                assertEquals(listOf(102L, 103L, 104L), latest().graph.descendantsOf(100L).map { it.no })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `autoplay follows the settings policy`() = runTest(dispatcher.scheduler) {
        val env = Env(listOf(post(100)))
        env.vm().uiState.test {
            env.settings.state.value = Settings(mediaAutoplay = MediaAutoplay.ALWAYS)
            assertTrue(latest().autoplay)
            env.settings.state.value = Settings(mediaAutoplay = MediaAutoplay.NEVER)
            assertFalse(latest().autoplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `defaultUnmuted mirrors the board webm_audio flag`() = runTest(dispatcher.scheduler) {
        val env = Env(listOf(post(100)), boards = FakeBoardRepository(webmAudio = true))
        env.vm().uiState.test {
            assertTrue(latest().defaultUnmuted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `saved paths are hidden without storage access`() = runTest(dispatcher.scheduler) {
        val env = Env(listOf(post(100)))
        env.vault.urls.value = setOf("https://i/100.jpg")
        env.vault.paths.value = mapOf("https://i/100.jpg" to "/vault/100.jpg")
        val vm = env.vm()
        vm.uiState.test {
            val withAccess = latest()
            assertEquals(setOf("https://i/100.jpg"), withAccess.downloadedUrls)
            assertEquals("/vault/100.jpg", withAccess.savedPaths["https://i/100.jpg"])
            env.vault.access = false
            assertFalse(vm.hasStorageAccess())
            // Access isn't a flow; nudge any upstream so the combine re-evaluates.
            env.vault.paths.value = env.vault.paths.value.toMap()
            env.vault.urls.value = env.vault.urls.value + "https://i/101.jpg"
            assertTrue(latest().savedPaths.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `enqueueSave carries the OP-derived context plus the item's own post`() =
        runTest(dispatcher.scheduler) {
            val env = Env(listOf(post(100), post(102)))
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle() // let the thread load fill saveContextBase
            vm.enqueueSave(media(102))
            val ctx = env.vault.firstSave.await() // real IO worker; runTest's own timeout guards a hang
            assertEquals("g", ctx.board)
            assertEquals(100L, ctx.threadNo)
            assertEquals("OP subject", ctx.threadSubject)
            assertEquals("body 100", ctx.opExcerpt)
            assertEquals(102L, ctx.post?.no)
        }

    @Test fun `removeDownload and redownload delete through the vault`() =
        runTest(dispatcher.scheduler) {
            val env = Env(listOf(post(100), post(102)))
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()
            vm.removeDownload("https://i/100.jpg")
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf("https://i/100.jpg"), env.vault.deleted)
            vm.redownload(media(102))
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf("https://i/100.jpg", "https://i/102.jpg"), env.vault.deleted)
            val ctx = env.vault.firstSave.await() // real IO worker; runTest's own timeout guards a hang
            assertEquals(102L, ctx.post?.no)
        }

    @Test fun `onMediaViewed records the post in the session store`() = runTest(dispatcher.scheduler) {
        val env = Env(listOf(post(100)))
        env.vm().onMediaViewed(103)
        assertEquals(103L, env.sessionStore.consumeLastViewed("g", 100))
        assertNull(env.sessionStore.consumeLastViewed("g", 100))
    }

    @Test fun `prepareShare copies the media into the share cache`() = runTest(dispatcher.scheduler) {
        val server = MockWebServer().apply { start() }
        try {
            val env = Env(listOf(post(100)))
            val item = media(100).copy(fullUrl = server.url("/i/100.jpg").toString())
            server.enqueue(MockResponse().setBody("jpeg-bytes"))
            val file = env.vm().prepareShare(item)
            assertEquals("img100.jpg", file?.name)
            assertEquals("jpeg-bytes", file?.readText())
            assertTrue(file!!.path.contains("shared_media"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun `prepareShare returns null when the fetch fails`() = runTest(dispatcher.scheduler) {
        val server = MockWebServer().apply { start() }
        try {
            val env = Env(listOf(post(100)))
            val item = media(100).copy(fullUrl = server.url("/gone.jpg").toString())
            server.enqueue(MockResponse().setResponseCode(404))
            assertNull(env.vm().prepareShare(item))
        } finally {
            server.shutdown()
        }
    }
}

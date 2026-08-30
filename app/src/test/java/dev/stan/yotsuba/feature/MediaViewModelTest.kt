package dev.stan.yotsuba.feature

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import dev.stan.yotsuba.core.media.MediaByteSource
import dev.stan.yotsuba.core.network.NetworkMonitor
import dev.stan.yotsuba.data.repository.MediaDownloadQueue
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.MediaAutoplay
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.PostSegment
import dev.stan.yotsuba.domain.model.PostText
import dev.stan.yotsuba.domain.model.SeekStep
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import dev.stan.yotsuba.fake.FakeMediaVault
import dev.stan.yotsuba.fake.FakeSettings
import dev.stan.yotsuba.fake.MainDispatcherRule
import dev.stan.yotsuba.fake.NoDedup
import dev.stan.yotsuba.fake.latest
import dev.stan.yotsuba.feature.media.MediaSessionStore
import dev.stan.yotsuba.feature.media.MediaUiState
import dev.stan.yotsuba.feature.media.MediaViewModel
import dev.stan.yotsuba.feature.media.ViewerPhase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric only because [NetworkMonitor] and [MediaByteSource] are concrete classes
 * over Context; the repositories are faked at their interfaces. ExoPlayer never appears
 * here. The ViewModel itself is player-free.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MediaViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule val mainDispatcherRule = MainDispatcherRule(dispatcher)

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

    /** Saves resolve into [saved], in order. */
    private class FakeVault : FakeMediaVault() {
        val access = MutableStateFlow(true)
        val saved = mutableListOf<Pair<MediaItem, VaultSaveContext>>()
        val deleted = mutableListOf<String>()
        val paths = MutableStateFlow(emptyMap<String, String?>())
        override fun hasStorageAccess() = access.value
        override val storageAccess: Flow<Boolean> = access
        override fun saved(): Flow<Map<String, String?>> = paths
        override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? {
            saved += item to context
            return null
        }
        override suspend fun delete(url: String): VaultError? {
            deleted += url
            return null
        }
        var snapshot: ThreadDetails? = null
        override suspend fun savedThread(board: String, threadNo: Long): ThreadDetails? = snapshot
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
        /** The save queue's worker runs here, on the test dispatcher; `runCurrent` lands the saves. */
        scope: CoroutineScope,
        io: CoroutineDispatcher,
        posts: List<ThreadPost>,
        backlinks: Map<Long, List<Long>> = emptyMap(),
        val boards: FakeBoardRepository = FakeBoardRepository(),
        val settings: FakeSettings = FakeSettings(),
        val vault: FakeVault = FakeVault(),
        val sessionStore: MediaSessionStore = MediaSessionStore(),
        val server: MockWebServer? = null,
        threadFails: Boolean = false,
    ) {
        val threads = FakeThreadRepository(
            ThreadDetails("g", 100, posts, archived = false, closed = false, backlinks = backlinks),
            fails = threadFails,
        )
        val queue = MediaDownloadQueue(vault, NoDedup, scope, io)
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

    private suspend fun TurbineTestContext<MediaUiState>.latest() = latest(dispatcher.scheduler)

    @Test fun `a dead thread falls back to the conversation saved on disk`() =
        runTest(dispatcher.scheduler) {
            val env = Env(backgroundScope, dispatcher, listOf(post(100)), threadFails = true)
            env.vault.snapshot = ThreadDetails(
                "g", 100,
                posts = listOf(post(100), post(101)),
                archived = false, closed = false,
                backlinks = mapOf(100L to listOf(101L)),
            )
            env.vm().uiState.test {
                val state = latest()
                assertEquals(setOf(100L, 101L), state.thread.byNo.keys)
                assertEquals(listOf(101L), state.thread.graph.descendantsOf(100L).map { it.no })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `viewer behaviour mirrors the settings that drive the gestures`() =
        runTest(dispatcher.scheduler) {
            val env = Env(backgroundScope, dispatcher, listOf(post(100)))
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
            val env = Env(backgroundScope, dispatcher, listOf(post(100), post(101, withMedia = false), post(102), post(103)))
            env.vm(initialPostNo = 102).uiState.test {
                val state = latest()
                assertEquals(listOf(100L, 102L, 103L), state.items.map { it.postNo })
                assertEquals(1, state.initialIndex)
                assertEquals(ViewerPhase.Ready, state.phase)
                assertEquals(setOf(100L, 101L, 102L, 103L), state.thread.byNo.keys)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `viewer goes Loading then Empty for a thread with no media`() =
        runTest(dispatcher.scheduler) {
            val env = Env(backgroundScope, dispatcher, listOf(post(100, withMedia = false), post(101, withMedia = false)))
            env.vm().uiState.test {
                assertEquals(ViewerPhase.Loading, awaitItem().phase)
                val state = latest()
                assertEquals(ViewerPhase.Empty, state.phase)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `viewer reports the network error when nothing is saved either`() =
        runTest(dispatcher.scheduler) {
            val env = Env(backgroundScope, dispatcher, listOf(post(100)), threadFails = true)
            env.vm().uiState.test {
                assertEquals(ViewerPhase.Error(NetworkError.NotFound), latest().phase)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `unknown initial post falls back to the first item`() = runTest(dispatcher.scheduler) {
        val env = Env(backgroundScope, dispatcher, listOf(post(100), post(102)))
        env.vm(initialPostNo = 999).uiState.test {
            assertEquals(0, latest().initialIndex)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `the post graph walks backlinks transitively in post order`() =
        runTest(dispatcher.scheduler) {
            // 100 <- 102 <- 103, and 100 <- 104 directly.
            val env = Env(backgroundScope, dispatcher, 
                posts = listOf(post(100), post(102), post(103), post(104)),
                backlinks = mapOf(100L to listOf(104L, 102L), 102L to listOf(103L)),
            )
            env.vm().uiState.test {
                assertEquals(listOf(102L, 103L, 104L), latest().thread.graph.descendantsOf(100L).map { it.no })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `autoplay follows the settings policy`() = runTest(dispatcher.scheduler) {
        val env = Env(backgroundScope, dispatcher, listOf(post(100)))
        env.vm().uiState.test {
            env.settings.state.value = Settings(mediaAutoplay = MediaAutoplay.ALWAYS)
            assertTrue(latest().autoplay)
            env.settings.state.value = Settings(mediaAutoplay = MediaAutoplay.NEVER)
            assertFalse(latest().autoplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `defaultUnmuted mirrors the board webm_audio flag`() = runTest(dispatcher.scheduler) {
        val env = Env(backgroundScope, dispatcher, listOf(post(100)), boards = FakeBoardRepository(webmAudio = true))
        env.vm().uiState.test {
            assertTrue(latest().defaultUnmuted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `saved paths are hidden without storage access`() = runTest(dispatcher.scheduler) {
        val env = Env(backgroundScope, dispatcher, listOf(post(100)))
        env.vault.paths.value = mapOf("https://i/100.jpg" to "/vault/100.jpg")
        val vm = env.vm()
        vm.uiState.test {
            val withAccess = latest()
            assertTrue("https://i/100.jpg" in withAccess.saved)
            assertEquals("/vault/100.jpg", withAccess.savedPath("https://i/100.jpg"))
            assertTrue(withAccess.hasStorageAccess)
            env.vault.access.value = false
            val withoutAccess = latest()
            assertFalse(withoutAccess.hasStorageAccess)
            assertTrue(withoutAccess.saved.isEmpty())
            assertFalse("https://i/100.jpg" in withoutAccess.saved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `conversation capture follows the setting the viewer already holds`() =
        runTest(dispatcher.scheduler) {
            val env = Env(backgroundScope, dispatcher, 
                posts = listOf(post(100), post(102, quotes = listOf(100))),
                backlinks = mapOf(100L to listOf(102L)),
            )
            env.settings.state.value = Settings(saveRepliesWithMedia = true)
            val vm = env.vm()
            vm.uiState.test {
                assertTrue(latest().saveReplies)
                vm.enqueueSave(media(100))
                dispatcher.scheduler.runCurrent()
                val ctx = env.vault.saved.single().second
                assertEquals(listOf(100L, 102L), ctx.conversation.map { it.no })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `enqueueSave carries the OP-derived context plus the item's own post`() =
        runTest(dispatcher.scheduler) {
            val env = Env(backgroundScope, dispatcher, listOf(post(100), post(102)))
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle() // let the thread load in, so the OP is known
            vm.enqueueSave(media(102))
            dispatcher.scheduler.runCurrent()
            val ctx = env.vault.saved.single().second
            assertEquals("g", ctx.board)
            assertEquals(100L, ctx.threadNo)
            assertEquals("OP subject", ctx.threadSubject)
            assertEquals("body 100", ctx.opExcerpt)
            assertEquals(102L, ctx.post?.no)
        }

    @Test fun `removeDownload and redownload delete through the vault`() =
        runTest(dispatcher.scheduler) {
            val env = Env(backgroundScope, dispatcher, listOf(post(100), post(102)))
            val vm = env.vm()
            dispatcher.scheduler.advanceUntilIdle()
            vm.removeDownload("https://i/100.jpg")
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf("https://i/100.jpg"), env.vault.deleted)
            vm.redownload(media(102))
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf("https://i/100.jpg", "https://i/102.jpg"), env.vault.deleted)
            dispatcher.scheduler.runCurrent()
            val ctx = env.vault.saved.single().second
            assertEquals(102L, ctx.post?.no)
        }

    @Test fun `onMediaViewed records the post in the session store`() = runTest(dispatcher.scheduler) {
        val env = Env(backgroundScope, dispatcher, listOf(post(100)))
        env.vm().onMediaViewed(103)
        assertEquals(103L, env.sessionStore.consumeLastViewed("g", 100))
        assertNull(env.sessionStore.consumeLastViewed("g", 100))
    }

    @Test fun `prepareShare copies the media into the share cache`() = runTest(dispatcher.scheduler) {
        val server = MockWebServer().apply { start() }
        try {
            val env = Env(backgroundScope, dispatcher, listOf(post(100)))
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

    @Test fun `prepareShare hands over the vault file instead of downloading again`() =
        runTest(dispatcher.scheduler) {
            val env = Env(backgroundScope, dispatcher, listOf(post(100)))
            val saved = java.io.File.createTempFile("vault", ".jpg").apply { writeText("on disk") }
            try {
                env.vault.paths.value = mapOf("https://i/100.jpg" to saved.absolutePath)
                val vm = env.vm()
                vm.uiState.test {
                    latest()
                    assertEquals(saved, vm.prepareShare(media(100)))
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                saved.delete()
            }
        }

    @Test fun `prepareShare keeps only the newest twenty cached files`() = runTest(dispatcher.scheduler) {
        val server = MockWebServer().apply { start() }
        try {
            val env = Env(backgroundScope, dispatcher, listOf(post(100)))
            val dir = java.io.File(env.context.cacheDir, "shared_media").apply { mkdirs() }
            repeat(25) { i ->
                java.io.File(dir, "old$i.jpg").apply {
                    writeText("x")
                    setLastModified(1_000_000L + i * 1_000)
                }
            }
            val item = media(100).copy(fullUrl = server.url("/i/100.jpg").toString())
            server.enqueue(MockResponse().setBody("jpeg-bytes"))
            val file = env.vm().prepareShare(item)
            val names = dir.list()!!.toSet()
            assertEquals(20, names.size)
            assertTrue(file!!.name in names)
            assertTrue("old24.jpg" in names)
            assertFalse("old0.jpg" in names)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `prepareShare returns null when the fetch fails`() = runTest(dispatcher.scheduler) {
        val server = MockWebServer().apply { start() }
        try {
            val env = Env(backgroundScope, dispatcher, listOf(post(100)))
            val item = media(100).copy(fullUrl = server.url("/gone.jpg").toString())
            server.enqueue(MockResponse().setResponseCode(404))
            assertNull(env.vm().prepareShare(item))
        } finally {
            server.shutdown()
        }
    }
}

package dev.stan.yotsuba.feature.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.stan.yotsuba.core.media.MediaByteSource
import dev.stan.yotsuba.core.network.NetworkMonitor
import dev.stan.yotsuba.data.repository.MediaDownloadQueue
import dev.stan.yotsuba.domain.model.ArchiveSource
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.PostSegment
import dev.stan.yotsuba.domain.model.PostText
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import dev.stan.yotsuba.fake.FakeMediaVault
import dev.stan.yotsuba.fake.FakeSettings
import dev.stan.yotsuba.fake.NoDedup
import dev.stan.yotsuba.fake.latest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The viewer resolves a thread the way the thread screen does: live, then the vault
 * snapshot, then an archive, but the archive only once 4chan has said 404.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaArchiveFallthroughTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeThreadRepository(
        private val live: DataResult<ThreadDetails>,
        private val archive: DataResult<ThreadDetails>,
    ) : ThreadRepository {
        var archiveAsked = 0
        override suspend fun thread(board: String, no: Long, forceRefresh: Boolean) = live
        override suspend fun archivedThread(board: String, no: Long): DataResult<ThreadDetails> {
            archiveAsked++
            return archive
        }
    }

    private class FakeBoardRepository : BoardRepository {
        override suspend fun boards(forceRefresh: Boolean) = DataResult.Success(emptyList<Board>())
        override suspend fun board(code: String) = Board(
            code = code, title = "Anime", description = "", worksafe = true,
            category = BoardCategory.INTERESTS, userIds = false, countryFlags = false,
            boardFlags = false, spoilers = false, webmAudio = false, codeTags = false,
            mathTags = false, sjisTags = false, textOnly = false,
        )
    }

    private class FakeVault(var snapshot: ThreadDetails? = null) : FakeMediaVault() {
        override suspend fun savedThread(board: String, threadNo: Long): ThreadDetails? = snapshot
    }

    private fun post(no: Long, fullUrl: String) = ThreadPost(
        board = "a", no = no, isOp = no == 100L, name = "Anonymous", tripcode = null,
        capcode = null, posterId = null, countryCode = null, countryName = null,
        timeSeconds = 0, subject = null, body = PostText(listOf(PostSegment("body $no"))),
        media = PostMedia.Present(
            MediaItem(
                postNo = no, filename = "img$no", ext = ".jpg", sizeBytes = 1,
                width = 10, height = 10, thumbnailUrl = "$fullUrl.thumb", fullUrl = fullUrl,
                spoiler = false,
            ),
        ),
        quotedPostNos = emptyList(),
    )

    private fun details(posts: List<ThreadPost>, archive: ArchiveSource? = null) =
        ThreadDetails("a", 100, posts, archived = false, closed = false, backlinks = emptyMap(), archive = archive)

    private val archiveUrl = "https://desu-usergeneratedcontent.xyz/a/image/100.jpg"
    private val archived = details(listOf(post(100, archiveUrl)), archive = ArchiveSource.DESU)

    private fun vm(scope: CoroutineScope, threads: FakeThreadRepository, vault: FakeVault): MediaViewModel {
        val context: Context = ApplicationProvider.getApplicationContext()
        return MediaViewModel(
            board = "a", threadNo = 100, initialPostNo = 0,
            appContext = context,
            threadRepository = threads,
            boardRepository = FakeBoardRepository(),
            settingsRepository = FakeSettings(),
            networkMonitor = NetworkMonitor(context),
            mediaVault = vault,
            downloadQueue = MediaDownloadQueue(vault, NoDedup, scope, dispatcher),
            byteSource = MediaByteSource(context, OkHttpClient()),
            sessionStore = MediaSessionStore(),
        )
    }

    @Test fun `live 404 then vault miss falls through to the archive copy`() =
        runTest(dispatcher.scheduler) {
            val threads = FakeThreadRepository(
                live = DataResult.Failure(NetworkError.NotFound),
                archive = DataResult.Success(archived),
            )
            vm(backgroundScope, threads, FakeVault()).uiState.test {
                val state = latest(dispatcher.scheduler)
                assertEquals(ViewerPhase.Ready, state.phase)
                assertEquals(listOf(archiveUrl), state.items.map { it.fullUrl })
                assertEquals(1, threads.archiveAsked)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `the vault snapshot still wins over the archive`() = runTest(dispatcher.scheduler) {
        val threads = FakeThreadRepository(
            live = DataResult.Failure(NetworkError.NotFound),
            archive = DataResult.Success(archived),
        )
        val vault = FakeVault(snapshot = details(listOf(post(100, "https://i.4cdn.org/a/100.jpg"))))
        vm(backgroundScope, threads, vault).uiState.test {
            assertEquals(listOf("https://i.4cdn.org/a/100.jpg"), latest(dispatcher.scheduler).items.map { it.fullUrl })
            assertEquals(0, threads.archiveAsked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `a failure other than 404 never asks the archive`() = runTest(dispatcher.scheduler) {
        val threads = FakeThreadRepository(
            live = DataResult.Failure(NetworkError.Offline),
            archive = DataResult.Success(archived),
        )
        vm(backgroundScope, threads, FakeVault()).uiState.test {
            assertEquals(ViewerPhase.Error(NetworkError.Offline), latest(dispatcher.scheduler).phase)
            assertEquals(0, threads.archiveAsked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `an archive miss reports the original 404`() = runTest(dispatcher.scheduler) {
        val threads = FakeThreadRepository(
            live = DataResult.Failure(NetworkError.NotFound),
            archive = DataResult.Failure(NetworkError.NotFound),
        )
        vm(backgroundScope, threads, FakeVault()).uiState.test {
            assertEquals(ViewerPhase.Error(NetworkError.NotFound), latest(dispatcher.scheduler).phase)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

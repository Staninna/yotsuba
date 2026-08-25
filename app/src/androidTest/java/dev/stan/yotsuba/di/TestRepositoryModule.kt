package dev.stan.yotsuba.di

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dev.stan.yotsuba.core.di.RepositoryModule
import dev.stan.yotsuba.core.text.PostSegment
import dev.stan.yotsuba.core.text.PostText
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.CatalogRepository
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.MaintenanceRepository
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Deterministic seed data every UI test drives against. No network, no Room. */
object TestSeed {
    const val BOARD = "g"
    const val BOARD_TITLE = "Technology"
    const val THREAD_NO = 1000L
    const val THREAD_SUBJECT = "Yotsuba test thread"
    const val OP_TEXT = "This is the seeded OP post body"
    const val REPLY_TEXT = "This is the seeded reply with an image"
    const val MEDIA_FILENAME = "seeded_image"

    private fun text(s: String) = PostText(listOf(PostSegment(s)))

    val board = Board(
        code = BOARD,
        title = BOARD_TITLE,
        description = "Technology board",
        worksafe = true,
        category = BoardCategory.INTERESTS,
        userIds = false,
        countryFlags = false,
        boardFlags = false,
        spoilers = false,
        webmAudio = false,
        codeTags = true,
        mathTags = false,
        sjisTags = false,
        textOnly = false,
    )

    val catalogThread = CatalogThread(
        board = BOARD,
        no = THREAD_NO,
        subject = THREAD_SUBJECT,
        excerpt = text(OP_TEXT),
        thumbnailUrl = null,
        replyCount = 1,
        imageCount = 1,
        lastModified = 1_700_000_000L,
        sticky = false,
        closed = false,
    )

    val mediaItem = MediaItem(
        postNo = THREAD_NO + 1,
        filename = MEDIA_FILENAME,
        ext = ".png",
        sizeBytes = 12_345L,
        width = 800,
        height = 600,
        thumbnailUrl = "https://example.invalid/thumb.jpg",
        fullUrl = "https://example.invalid/full.png",
        spoiler = false,
    )

    private fun post(
        no: Long,
        isOp: Boolean,
        subject: String?,
        body: String,
        media: PostMedia?,
    ) = ThreadPost(
        board = BOARD,
        no = no,
        isOp = isOp,
        name = "Anonymous",
        tripcode = null,
        capcode = null,
        posterId = null,
        countryCode = null,
        countryName = null,
        timeSeconds = 1_700_000_000L,
        subject = subject,
        body = text(body),
        media = media,
        quotedPostNos = emptyList(),
    )

    val threadDetails = ThreadDetails(
        board = BOARD,
        threadNo = THREAD_NO,
        posts = listOf(
            post(THREAD_NO, isOp = true, subject = THREAD_SUBJECT, body = OP_TEXT, media = null),
            post(THREAD_NO + 1, isOp = false, subject = null, body = REPLY_TEXT, media = PostMedia.Present(mediaItem)),
        ),
        archived = false,
        closed = false,
        backlinks = emptyMap(),
    )
}

@Singleton
class FakeBoardRepository @Inject constructor() : BoardRepository {
    override suspend fun boards(forceRefresh: Boolean): DataResult<List<Board>> =
        DataResult.Success(listOf(TestSeed.board))

    override suspend fun board(code: String): Board? =
        TestSeed.board.takeIf { it.code == code }
}

@Singleton
class FakeCatalogRepository @Inject constructor() : CatalogRepository {
    override suspend fun catalog(board: String, forceRefresh: Boolean): DataResult<List<CatalogThread>> =
        if (board == TestSeed.BOARD) DataResult.Success(listOf(TestSeed.catalogThread))
        else DataResult.Success(emptyList())
}

@Singleton
class FakeThreadRepository @Inject constructor() : ThreadRepository {
    override suspend fun thread(board: String, no: Long, forceRefresh: Boolean): DataResult<ThreadDetails> =
        if (board == TestSeed.BOARD && no == TestSeed.THREAD_NO) DataResult.Success(TestSeed.threadDetails)
        else DataResult.Failure(NetworkError.NotFound)
}

@Singleton
class FakeBookmarkRepository @Inject constructor() : BookmarkRepository {
    private val state = MutableStateFlow<List<Bookmark>>(emptyList())
    override val bookmarks: Flow<List<Bookmark>> = state

    override suspend fun add(bookmark: Bookmark) {
        state.update { list ->
            list.filterNot { it.board == bookmark.board && it.threadNo == bookmark.threadNo } + bookmark
        }
    }

    override suspend fun remove(board: String, threadNo: Long) {
        state.update { list -> list.filterNot { it.board == board && it.threadNo == threadNo } }
    }

    override fun isBookmarked(board: String, threadNo: Long): Flow<Boolean> =
        state.map { list -> list.any { it.board == board && it.threadNo == threadNo } }

    override suspend fun refreshOne(bookmark: Bookmark): Bookmark =
        bookmark.copy(state = BookmarkState.ALIVE, lastCheckedAt = 1_700_000_100L)

    override suspend fun markSeen(board: String, threadNo: Long, lastSeenPostNo: Long, replyCount: Int) {
        state.update { list ->
            list.map {
                if (it.board == board && it.threadNo == threadNo) {
                    it.copy(lastSeenPostNo = lastSeenPostNo, newReplies = 0, unreadCount = 0)
                } else it
            }
        }
    }

    override suspend fun updateUnread(board: String, threadNo: Long, unread: Int) {
        state.update { list ->
            list.map {
                if (it.board == board && it.threadNo == threadNo) it.copy(unreadCount = unread) else it
            }
        }
    }

    override suspend fun clearAll() {
        state.value = emptyList()
    }
}

@Singleton
class FakeHistoryRepository @Inject constructor() : HistoryRepository {
    private val state = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private val scrollPositions = mutableMapOf<Pair<String, Long>, Long>()
    private val readMarks = mutableMapOf<Pair<String, Long>, Long>()

    override val history: Flow<List<HistoryEntry>> = state

    override suspend fun record(entry: HistoryEntry) {
        state.update { list ->
            list.filterNot { it.board == entry.board && it.threadNo == entry.threadNo } + entry
        }
    }

    override suspend fun updateScrollPosition(board: String, threadNo: Long, postNo: Long) {
        scrollPositions[board to threadNo] = postNo
    }

    override suspend fun lastScrollPosition(board: String, threadNo: Long): Long? =
        scrollPositions[board to threadNo]

    override suspend fun updateReadUpTo(board: String, threadNo: Long, postNo: Long) {
        val key = board to threadNo
        readMarks[key] = maxOf(readMarks[key] ?: 0L, postNo)
    }

    override suspend fun readUpTo(board: String, threadNo: Long): Long? = readMarks[board to threadNo]

    override suspend fun remove(board: String, threadNo: Long) {
        state.update { list -> list.filterNot { it.board == board && it.threadNo == threadNo } }
    }

    override suspend fun clearAll() {
        state.value = emptyList()
    }

    override suspend fun trim(retainAfterMs: Long) = Unit
}

@Singleton
class FakeHiddenThreadsRepository @Inject constructor() : HiddenThreadsRepository {
    private val state = MutableStateFlow<List<HiddenThread>>(emptyList())
    override val all: Flow<List<HiddenThread>> = state

    override fun forBoard(board: String): Flow<List<HiddenThread>> =
        state.map { list -> list.filter { it.board == board } }

    override suspend fun hide(board: String, threadNo: Long) {
        state.update { it + HiddenThread(board, threadNo) }
    }

    override suspend fun unhide(board: String, threadNo: Long) {
        state.update { list -> list.filterNot { it.board == board && it.threadNo == threadNo } }
    }
}

@Singleton
class FakeMediaVaultRepository @Inject constructor() : MediaVaultRepository {
    private val state = MutableStateFlow<List<VaultEntry>>(emptyList())

    override fun hasStorageAccess(): Boolean = true
    override fun entries(): Flow<List<VaultEntry>> = state
    override fun savedUrls(): Flow<Set<String>> = state.map { list -> list.map { it.url }.toSet() }
    override fun savedPaths(): Flow<Map<String, String>> =
        state.map { list -> list.associate { it.url to it.absolutePath } }

    override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? = null
    override suspend fun delete(url: String): VaultError? {
        state.update { list -> list.filterNot { it.url == url } }
        return null
    }

    override suspend fun rescan() = Unit
    override suspend fun migrateLegacyIfNeeded() = Unit
}

@Singleton
class FakeSettingsRepository @Inject constructor() : SettingsRepository {
    val state = MutableStateFlow(Settings())
    override val settings: Flow<Settings> = state

    override suspend fun update(transform: (Settings) -> Settings) {
        state.update(transform)
    }
}

@Singleton
class FakeMaintenanceRepository @Inject constructor() : MaintenanceRepository {
    override suspend fun clearCaches() = Unit
}

/** Replaces every production repository binding with in-memory fakes for instrumented tests. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RepositoryModule::class])
abstract class TestRepositoryModule {
    @Binds abstract fun boardRepository(impl: FakeBoardRepository): BoardRepository
    @Binds abstract fun catalogRepository(impl: FakeCatalogRepository): CatalogRepository
    @Binds abstract fun threadRepository(impl: FakeThreadRepository): ThreadRepository
    @Binds abstract fun bookmarkRepository(impl: FakeBookmarkRepository): BookmarkRepository
    @Binds abstract fun historyRepository(impl: FakeHistoryRepository): HistoryRepository
    @Binds abstract fun settingsRepository(impl: FakeSettingsRepository): SettingsRepository
    @Binds abstract fun mediaVaultRepository(impl: FakeMediaVaultRepository): MediaVaultRepository
    @Binds abstract fun hiddenThreadsRepository(impl: FakeHiddenThreadsRepository): HiddenThreadsRepository
    @Binds abstract fun maintenanceRepository(impl: FakeMaintenanceRepository): MaintenanceRepository
}

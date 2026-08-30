package dev.stan.yotsuba.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dev.stan.yotsuba.core.di.RepositoryModule
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.DataResult
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.ImportSource
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.NetworkError
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.PostSegment
import dev.stan.yotsuba.domain.model.PostText
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultError
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.domain.model.VaultSaveContext
import dev.stan.yotsuba.domain.model.VaultSyncSummary
import dev.stan.yotsuba.domain.repository.BackupRepository
import dev.stan.yotsuba.domain.repository.BoardRepository
import dev.stan.yotsuba.domain.repository.BookmarkRefreshSummary
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.CatalogRepository
import dev.stan.yotsuba.domain.repository.ClaimedPostRepository
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.MaintenanceRepository
import dev.stan.yotsuba.domain.repository.MediaSaveQueue
import dev.stan.yotsuba.domain.repository.MediaVaultRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.ThreadRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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
    const val SPOILER_REPLY_TEXT = "Reply hiding a surprise picture"
    const val SPOILER_FILENAME = "spoiler_image"

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

    val spoilerMediaItem = MediaItem(
        postNo = THREAD_NO + 2,
        filename = SPOILER_FILENAME,
        ext = ".png",
        sizeBytes = 6_789L,
        width = 640,
        height = 480,
        thumbnailUrl = "https://example.invalid/spoiler_thumb.jpg",
        fullUrl = "https://example.invalid/spoiler_full.png",
        spoiler = true,
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
            post(THREAD_NO + 2, isOp = false, subject = null, body = SPOILER_REPLY_TEXT, media = PostMedia.Present(spoilerMediaItem)),
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

    override suspend fun markSeen(board: String, threadNo: Long, postNo: Long) {
        state.update { list ->
            list.map {
                if (it.board == board && it.threadNo == threadNo) {
                    it.copy(readUpTo = maxOf(it.readUpTo ?: 0, postNo))
                } else it
            }
        }
    }

    override suspend fun refreshAll(onProgress: (Int, Int) -> Unit) = BookmarkRefreshSummary()
    override suspend fun setPinned(board: String, threadNo: Long, pinned: Boolean) {
        state.update { list ->
            list.map { if (it.board == board && it.threadNo == threadNo) it.copy(pinned = pinned) else it }
        }
    }
    override suspend fun removeDead() = Unit

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

    override suspend fun restore(entry: HistoryEntry) = record(entry)

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
    override val storageAccess: Flow<Boolean> = flowOf(true)
    override fun refreshStorageAccess() = Unit
    override fun entries(): Flow<List<VaultEntry>> = state
    override fun saved(): Flow<Map<String, String?>> =
        state.map { list -> list.associate { it.url to it.absolutePath } }

    override suspend fun save(item: MediaItem, context: VaultSaveContext): VaultError? = saveNow(item, context)

    /** Mirrors production: a successful save lands in the entries flow, so badges flip to SAVED. */
    fun saveNow(item: MediaItem, context: VaultSaveContext): VaultError? {
        val entry = VaultEntry(
            url = item.fullUrl,
            location = VaultLocation(context.board, context.threadNo),
            subject = context.threadSubject,
            postNo = item.postNo,
            displayName = item.displayName,
            absolutePath = "/fake-vault/${item.displayName}",
            ext = item.ext,
            sizeBytes = item.sizeBytes,
            width = item.width,
            height = item.height,
            thumbnailUrl = item.thumbnailUrl,
            savedAt = 1_700_000_200L,
        )
        state.update { list -> list.filterNot { it.url == entry.url } + entry }
        return null
    }
    override suspend fun delete(url: String): VaultError? {
        state.update { list -> list.filterNot { it.url == url } }
        return null
    }

    override suspend fun trash(url: String): VaultError? = delete(url)
    override suspend fun restoreTrashed(url: String): VaultError? = VaultError.NotFound
    override suspend fun purgeTrash() = Unit
    override suspend fun exportToGallery(url: String): VaultError? = null

    override suspend fun syncSavedThreads(onProgress: (Int, Int) -> Unit, skip: Set<VaultLocation>) = VaultSyncSummary()
    override suspend fun snapshotThread(board: String, threadNo: Long): VaultError? = null
    override suspend fun snapshotThreads(targets: List<VaultLocation>, onProgress: (Int, Int) -> Unit) = VaultSyncSummary()
    override suspend fun renameThread(board: String, threadNo: Long, name: String): VaultError? = null
    override suspend fun mergeThreads(fromBoard: String, fromThreadNo: Long, intoBoard: String, intoThreadNo: Long): VaultError? = null

    override suspend fun importLocalThread(name: String, sources: List<ImportSource>): VaultError? = null

    override suspend fun savedThread(board: String, threadNo: Long): ThreadDetails? = null

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

@Singleton
class FakeClaimedPostRepository @Inject constructor() : ClaimedPostRepository {
    private val state = MutableStateFlow<Set<Triple<String, Long, Long>>>(emptySet())

    override fun claimed(board: String, threadNo: Long): Flow<Set<Long>> = state.map { all ->
        all.filter { it.first == board && it.second == threadNo }.map { it.third }.toSet()
    }

    override suspend fun claim(board: String, threadNo: Long, postNo: Long) {
        state.update { it + Triple(board, threadNo, postNo) }
    }

    override suspend fun unclaim(board: String, threadNo: Long, postNo: Long) {
        state.update { it - Triple(board, threadNo, postNo) }
    }
}

/**
 * Saves straight through to the vault fake on the caller's thread, so a badge flips to
 * SAVED before the test's next assertion without any waiting.
 */
@Singleton
class FakeMediaSaveQueue @Inject constructor(
    private val vault: FakeMediaVaultRepository,
) : MediaSaveQueue {
    private val failed = MutableStateFlow<Map<String, MediaSaveStatus>>(emptyMap())

    override val statuses: Flow<Map<String, MediaSaveStatus>> = combine(vault.saved(), failed) { saved, failed ->
        failed + saved.keys.associateWith { MediaSaveStatus.Saved }
    }

    override fun enqueue(item: MediaItem, context: VaultSaveContext) {
        val error = vault.saveNow(item, context)
        failed.update { if (error == null) it - item.fullUrl else it + (item.fullUrl to MediaSaveStatus.Failed(error)) }
    }

    override fun cancel(url: String) = Unit
    override fun retry(url: String) = Unit
    override fun dismiss(url: String) = failed.update { it - url }
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
    @Binds abstract fun claimedPostRepository(impl: FakeClaimedPostRepository): ClaimedPostRepository
    @Binds abstract fun mediaSaveQueue(impl: FakeMediaSaveQueue): MediaSaveQueue

    companion object {
        /** No vault to write to in a UI test, so nothing is ever exported or found. */
        @Provides
        @Singleton
        fun backupRepository(): BackupRepository = BackupRepository.None
    }
}

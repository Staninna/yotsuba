package dev.stan.yotsuba.di

import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.HiddenThread
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.HistoryRetention
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.UsageEvent
import dev.stan.yotsuba.domain.model.UsageKind
import dev.stan.yotsuba.domain.repository.BookmarkRefreshSummary
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.ClaimedPostRepository
import dev.stan.yotsuba.domain.repository.HiddenThreadsRepository
import dev.stan.yotsuba.domain.repository.HistoryRepository
import dev.stan.yotsuba.domain.repository.MaintenanceRepository
import dev.stan.yotsuba.domain.repository.SettingsRepository
import dev.stan.yotsuba.domain.repository.UsageRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/*
 * Everything the user owns: bookmarks, history, hidden threads, claimed posts, settings,
 * usage events. All in memory, all observable, all seedable before launch.
 */

@Singleton
class FakeBookmarkRepository @Inject constructor() : BookmarkRepository {
    val state = MutableStateFlow<List<Bookmark>>(emptyList())
    override val bookmarks: Flow<List<Bookmark>> = state
    /** What [refreshAll] reports; the progress callback counts up to [refreshProgressSteps]. */
    var refreshSummary = BookmarkRefreshSummary()
    var refreshProgressSteps = 0
    var refreshCalls = 0
    var removeDeadCalls = 0

    fun seed(vararg items: Bookmark) {
        state.value = items.toList()
    }

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
                if (it.board == board && it.threadNo == threadNo) it.copy(readUpTo = maxOf(it.readUpTo ?: 0, postNo)) else it
            }
        }
    }

    override suspend fun refreshAll(onProgress: (Int, Int) -> Unit): BookmarkRefreshSummary {
        refreshCalls++
        repeat(refreshProgressSteps) {
            onProgress(it + 1, refreshProgressSteps)
            // A real pass spends about a second per board. Without a pause the whole count
            // lands inside one frame and "Checking i/N" never reaches the screen.
            delay(150)
        }
        return refreshSummary
    }

    override suspend fun setPinned(board: String, threadNo: Long, pinned: Boolean) {
        state.update { list ->
            list.map { if (it.board == board && it.threadNo == threadNo) it.copy(pinned = pinned) else it }
        }
    }

    override suspend fun removeDead() {
        removeDeadCalls++
        state.update { list -> list.filterNot { it.isDead } }
    }

    override suspend fun clearAll() {
        state.value = emptyList()
    }
}

/**
 * Also the usage log's owner, as [dev.stan.yotsuba.data.repository.HistoryRepositoryImpl] is:
 * the log is reading history by another name, so clearing and trimming reach it from here,
 * and the retention preference is applied on every write. Trimming the history table itself
 * is the DAO's business in production, so [trim] only records the call.
 */
@Singleton
class FakeHistoryRepository @Inject constructor(
    private val settings: FakeSettingsRepository,
    private val usage: FakeUsageRepository,
) : HistoryRepository {
    val state = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val scrollPositions = mutableMapOf<Pair<String, Long>, Long>()
    val readMarks = mutableMapOf<Pair<String, Long>, Long>()
    var trimCalls = 0

    override val history: Flow<List<HistoryEntry>> = state

    fun seed(vararg items: HistoryEntry) {
        state.value = items.toList()
    }

    override suspend fun record(entry: HistoryEntry) {
        restore(entry)
        val cutoff = when (settings.state.value.historyRetention) {
            HistoryRetention.FOREVER -> return
            HistoryRetention.DAYS_30 -> System.currentTimeMillis() - 30L * 86_400_000
            HistoryRetention.DAYS_7 -> System.currentTimeMillis() - 7L * 86_400_000
        }
        trim(cutoff)
    }

    override suspend fun updateScrollPosition(board: String, threadNo: Long, postNo: Long) {
        scrollPositions[board to threadNo] = postNo
    }

    override suspend fun lastScrollPosition(board: String, threadNo: Long): Long? = scrollPositions[board to threadNo]

    override suspend fun updateReadUpTo(board: String, threadNo: Long, postNo: Long) {
        val key = board to threadNo
        readMarks[key] = maxOf(readMarks[key] ?: 0L, postNo)
    }

    override suspend fun readUpTo(board: String, threadNo: Long): Long? = readMarks[board to threadNo]

    override suspend fun remove(board: String, threadNo: Long) {
        state.update { list -> list.filterNot { it.board == board && it.threadNo == threadNo } }
    }

    /** An undone removal: it goes back in, and unlike [record] it trims nothing. */
    override suspend fun restore(entry: HistoryEntry) {
        state.update { list ->
            list.filterNot { it.board == entry.board && it.threadNo == entry.threadNo } + entry
        }
    }

    override suspend fun clearAll() {
        state.value = emptyList()
        usage.clearAll()
    }

    override suspend fun trim(retainAfterMs: Long) {
        trimCalls++
        usage.trim(retainAfterMs)
    }
}

@Singleton
class FakeHiddenThreadsRepository @Inject constructor() : HiddenThreadsRepository {
    val state = MutableStateFlow<List<HiddenThread>>(emptyList())
    override val all: Flow<List<HiddenThread>> = state

    fun seed(vararg items: HiddenThread) {
        state.value = items.toList()
    }

    override fun forBoard(board: String): Flow<List<HiddenThread>> = state.map { list -> list.filter { it.board == board } }

    override suspend fun hide(board: String, threadNo: Long) {
        state.update { it + HiddenThread(board, threadNo) }
    }

    override suspend fun unhide(board: String, threadNo: Long) {
        state.update { list -> list.filterNot { it.board == board && it.threadNo == threadNo } }
    }
}

@Singleton
class FakeClaimedPostRepository @Inject constructor() : ClaimedPostRepository {
    val state = MutableStateFlow<Set<Triple<String, Long, Long>>>(emptySet())

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

@Singleton
class FakeSettingsRepository @Inject constructor() : SettingsRepository {
    val state = MutableStateFlow(Settings())
    override val settings: Flow<Settings> = state

    /**
     * Every value the app wrote, in order. A write that only reorders a Set (Home's
     * favourite tabs) produces a Settings that `equals` the last one, and StateFlow drops
     * those, so [state] never shows it. This is where a test sees such a write.
     */
    val writes = mutableListOf<Settings>()

    /** Shorthand for `state.update`, for `seed()` blocks. */
    fun set(transform: (Settings) -> Settings) = state.update(transform)

    override suspend fun update(transform: (Settings) -> Settings) {
        state.update { current -> transform(current).also { writes += it } }
    }
}

@Singleton
class FakeMaintenanceRepository @Inject constructor() : MaintenanceRepository {
    var clearCalls = 0
    var failWith: Throwable? = null

    override suspend fun clearCaches() {
        clearCalls++
        failWith?.let { throw it }
    }
}

@Singleton
class FakeUsageRepository @Inject constructor() : UsageRepository {
    val state = MutableStateFlow<List<UsageEvent>>(emptyList())
    /** Wall clock for new events; fix it so the Stats page's dates are predictable. */
    var now: () -> Long = System::currentTimeMillis
    var trimCalls = 0

    override fun events(): Flow<List<UsageEvent>> = state

    fun seed(vararg items: UsageEvent) {
        state.value = items.toList()
    }

    override fun record(kind: UsageKind, board: String?, threadNo: Long?, value: Long?) {
        state.update { it + UsageEvent(kind, now(), board, threadNo, value) }
    }

    override suspend fun clear(kind: UsageKind) {
        state.update { events -> events.filterNot { it.kind == kind } }
    }

    override suspend fun clearAll() {
        state.value = emptyList()
    }

    override suspend fun trim(retainAfterMs: Long) {
        trimCalls++
        state.update { events -> events.filter { it.at >= retainAfterMs } }
    }

    fun count(kind: UsageKind) = state.value.count { it.kind == kind }
}

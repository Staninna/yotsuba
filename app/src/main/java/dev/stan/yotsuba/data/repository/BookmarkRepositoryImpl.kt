package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.database.dao.BookmarkDao
import dev.stan.yotsuba.core.network.FourChanApi
import dev.stan.yotsuba.core.network.dto.ThreadDto
import dev.stan.yotsuba.core.util.DataResult
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.core.util.apiResult
import dev.stan.yotsuba.core.work.BookmarkRefreshScheduler
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.repository.BookmarkRefreshSummary
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import dev.stan.yotsuba.domain.repository.CatalogRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val dao: BookmarkDao,
    private val api: FourChanApi,
    private val catalogRepository: CatalogRepository,
    scheduler: BookmarkRefreshScheduler,
) : BookmarkRepository {

    /** Test seam; the injected constructor keeps wall-clock time. */
    internal var clock: () -> Long = System::currentTimeMillis

    init {
        // The Application class isn't ours to edit, so the periodic worker is registered the
        // first time the repository is built (KEEP policy: a no-op once it exists).
        scheduler.ensureScheduled()
    }

    override val bookmarks: Flow<List<Bookmark>> =
        dao.all().map { list -> list.map { it.toDomain() } }

    override suspend fun add(bookmark: Bookmark) = dao.upsert(bookmark.toEntity())

    override suspend fun remove(board: String, threadNo: Long) = dao.delete(board, threadNo)

    override fun isBookmarked(board: String, threadNo: Long): Flow<Boolean> =
        dao.isBookmarked(board, threadNo)

    /**
     * One bookmark, full JSON, no cache. A 404 flips the row to DEAD and the snapshot
     * stays (D9); `archived == 1` is ARCHIVED, still readable.
     */
    override suspend fun refreshOne(bookmark: Bookmark): Bookmark {
        val refreshed = when (val result = fetchThread(bookmark)) {
            is DataResult.Success -> result.value
            is DataResult.Failure -> when (result.error) {
                NetworkError.NotFound -> bookmark.copy(state = BookmarkState.DEAD, lastCheckedAt = clock())
                else -> bookmark
            }
        }
        dao.updateRefresh(
            refreshed.board, refreshed.threadNo, refreshed.replyCount, refreshed.imageCount,
            refreshed.state.name, refreshed.lastCheckedAt, refreshed.lastActivityAt,
            encodePostNos(refreshed.postNos),
        )
        return refreshed
    }

    override suspend fun refreshAll(onProgress: (done: Int, total: Int) -> Unit): BookmarkRefreshSummary {
        val live = bookmarks.first().filter { it.isLive }
        val byBoard = live.groupBy { it.board }
        onProgress(0, byBoard.size)
        var checked = 0
        var newUnread = 0
        var threadsWithNew = 0
        byBoard.entries.forEachIndexed { index, (board, marks) ->
            val catalog = catalogRepository.catalog(board, forceRefresh = true)
            if (catalog is DataResult.Success) {
                val entries = catalog.value.associateBy { it.no }
                for (mark in marks) {
                    val before = mark.unread
                    val after = refreshAgainstCatalog(mark, entries[mark.threadNo])
                    checked++
                    if (after.unread > before) {
                        newUnread += after.unread - before
                        threadsWithNew++
                    }
                }
            }
            onProgress(index + 1, byBoard.size)
        }
        return BookmarkRefreshSummary(checked, newUnread, threadsWithNew)
    }

    /** Decides per row whether the catalog entry is enough or the thread JSON is needed. */
    private suspend fun refreshAgainstCatalog(mark: Bookmark, entry: CatalogThread?): Bookmark {
        val now = clock()
        if (entry == null) {
            // Gone from the catalog: archived (still fetchable) or pruned (404). One JSON call
            // settles which, and the row is skipped from then on.
            val state = when (val r = fetchThread(mark)) {
                is DataResult.Success ->
                    if (r.value.state == BookmarkState.ARCHIVED) BookmarkState.ARCHIVED else BookmarkState.DEAD
                is DataResult.Failure ->
                    if (r.error == NetworkError.NotFound) BookmarkState.DEAD else return mark
            }
            dao.updateState(mark.board, mark.threadNo, state.name, now)
            return mark.copy(state = state, lastCheckedAt = now)
        }
        val activity = entry.lastModified.toEpochMs()
        if (entry.replyCount == mark.replyCount && mark.postNos.isNotEmpty()) {
            dao.updateCounts(
                mark.board, mark.threadNo, entry.replyCount, entry.imageCount,
                BookmarkState.ALIVE.name, now, activity,
            )
            return mark.copy(
                replyCount = entry.replyCount, imageCount = entry.imageCount,
                state = BookmarkState.ALIVE, lastCheckedAt = now, lastActivityAt = activity,
            )
        }
        // Reply count moved (or the post list was never captured): learn the new post numbers.
        return when (val r = fetchThread(mark)) {
            is DataResult.Success -> {
                val fresh = r.value.copy(lastActivityAt = activity)
                dao.updateRefresh(
                    fresh.board, fresh.threadNo, fresh.replyCount, fresh.imageCount,
                    fresh.state.name, fresh.lastCheckedAt, fresh.lastActivityAt, encodePostNos(fresh.postNos),
                )
                fresh
            }
            is DataResult.Failure -> {
                // Keep what the catalog said; the post list catches up next pass.
                dao.updateCounts(
                    mark.board, mark.threadNo, entry.replyCount, entry.imageCount,
                    BookmarkState.ALIVE.name, now, activity,
                )
                mark.copy(
                    replyCount = entry.replyCount, imageCount = entry.imageCount,
                    lastCheckedAt = now, lastActivityAt = activity,
                )
            }
        }
    }

    private suspend fun fetchThread(mark: Bookmark): DataResult<Bookmark> = apiResult {
        val dto = api.thread(mark.board, mark.threadNo, cacheControl = "no-cache")
        mark.applyThread(dto, clock())
    }

    private fun Bookmark.applyThread(dto: ThreadDto, now: Long): Bookmark {
        val op = dto.posts.firstOrNull()
        val last = dto.posts.lastOrNull()
        return copy(
            replyCount = op?.replies ?: replyCount,
            imageCount = op?.images ?: imageCount,
            state = if (op?.archived == 1) BookmarkState.ARCHIVED else BookmarkState.ALIVE,
            lastCheckedAt = now,
            postNos = dto.posts.drop(1).map { it.no },
            lastActivityAt = if (last != null && last.time > 0) last.time.toEpochMs() else lastActivityAt,
        )
    }

    override suspend fun markSeen(board: String, threadNo: Long, lastSeenPostNo: Long, replyCount: Int) =
        dao.markSeen(board, threadNo, lastSeenPostNo)

    @Deprecated("Unread is derived from readUpTo; use markSeen")
    override suspend fun updateUnread(board: String, threadNo: Long, unread: Int) = Unit

    override suspend fun setPinned(board: String, threadNo: Long, pinned: Boolean) =
        dao.setPinned(board, threadNo, pinned)

    override suspend fun removeDead() = dao.deleteDead()

    override suspend fun clearAll() = dao.clearAll()
}

/** 4chan timestamps are Unix seconds. */
private fun Long.toEpochMs(): Long = this * 1_000

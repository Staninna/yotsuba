package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.database.dao.BookmarkDao
import dev.stan.yotsuba.core.database.dao.HistoryDao
import dev.stan.yotsuba.core.database.entity.BookmarkEntity
import dev.stan.yotsuba.core.network.FourChanApi
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.core.util.toNetworkError
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.repository.BookmarkRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val dao: BookmarkDao,
    private val historyDao: HistoryDao,
    private val api: FourChanApi,
) : BookmarkRepository {

    override val bookmarks: Flow<List<Bookmark>> =
        dao.all().map { list -> list.map { it.toDomain() } }

    override suspend fun add(bookmark: Bookmark) = dao.upsert(bookmark.toEntity())

    override suspend fun remove(board: String, threadNo: Long) = dao.delete(board, threadNo)

    override suspend fun isBookmarked(board: String, threadNo: Long): Flow<Boolean> =
        dao.isBookmarked(board, threadNo)

    /** One bookmark refresh; a 404 flips the row to DEAD, the snapshot stays (D9). */
    override suspend fun refreshOne(bookmark: Bookmark): Bookmark = withContext(Dispatchers.IO) {
        val refreshed = try {
            val dto = api.thread(bookmark.board, bookmark.threadNo, cacheControl = "no-cache")
            val op = dto.posts.firstOrNull()
            val seen = bookmark.lastSeenPostNo
            bookmark.copy(
                replyCount = op?.replies ?: bookmark.replyCount,
                imageCount = op?.images ?: bookmark.imageCount,
                state = if (op?.archived == 1) BookmarkState.DEAD else BookmarkState.ALIVE,
                lastCheckedAt = System.currentTimeMillis(),
                newReplies = if (seen == null) 0 else dto.posts.count { it.no > seen },
                unreadCount = run {
                    // Truly unread = past the read high-water mark (bottom-most post that has
                    // been on screen); falls back to the last-visit marker.
                    val readUpTo = historyDao.maxRead(bookmark.board, bookmark.threadNo) ?: seen
                    if (readUpTo == null) 0 else dto.posts.count { it.no > readUpTo }
                },
            )
        } catch (e: Exception) {
            when (e.toNetworkError()) {
                NetworkError.NotFound -> bookmark.copy(
                    state = BookmarkState.DEAD,
                    lastCheckedAt = System.currentTimeMillis(),
                )
                else -> bookmark
            }
        }
        dao.updateRefresh(
            refreshed.board, refreshed.threadNo, refreshed.replyCount, refreshed.imageCount,
            refreshed.state.name, refreshed.lastCheckedAt, refreshed.newReplies, refreshed.unreadCount,
        )
        refreshed
    }

    override suspend fun markSeen(board: String, threadNo: Long, lastSeenPostNo: Long, replyCount: Int) =
        dao.markSeen(board, threadNo, lastSeenPostNo, replyCount)

    override suspend fun updateUnread(board: String, threadNo: Long, unread: Int) =
        dao.updateUnread(board, threadNo, unread)
}

fun BookmarkEntity.toDomain() = Bookmark(
    board, threadNo, subject, opExcerpt, thumbnailUrl, replyCount, imageCount,
    bookmarkedAt, lastCheckedAt, lastSeenPostNo,
    runCatching { BookmarkState.valueOf(state) }.getOrDefault(BookmarkState.UNKNOWN),
    newReplies,
    unreadCount,
)

fun Bookmark.toEntity() = BookmarkEntity(
    board, threadNo, subject, opExcerpt, thumbnailUrl, replyCount, imageCount,
    bookmarkedAt, lastCheckedAt, lastSeenPostNo, state.name, newReplies, unreadCount,
)

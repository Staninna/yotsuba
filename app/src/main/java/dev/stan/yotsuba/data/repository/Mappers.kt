package dev.stan.yotsuba.data.repository

import dev.stan.yotsuba.core.database.entity.BookmarkEntity
import dev.stan.yotsuba.core.database.entity.HistoryEntity
import dev.stan.yotsuba.core.network.dto.BoardDto
import dev.stan.yotsuba.core.network.dto.PostDto
import dev.stan.yotsuba.core.media.SoundPost
import dev.stan.yotsuba.core.text.PostAnnotation
import dev.stan.yotsuba.core.text.PostHtmlParser
import dev.stan.yotsuba.core.util.Urls
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.backlinksOf

fun BoardDto.toDomain(): Board = Board(
    code = board,
    title = title,
    description = PostHtmlParser.parse(meta_description).plainText,
    worksafe = ws_board == 1,
    category = BoardCategories.categoryOf(board),
    userIds = user_ids == 1,
    countryFlags = country_flags == 1,
    boardFlags = board_flags.isNotEmpty(),
    spoilers = spoilers == 1,
    webmAudio = webm_audio == 1,
    codeTags = code_tags == 1,
    mathTags = math_tags == 1,
    sjisTags = sjis_tags == 1,
    textOnly = text_only == 1,
)

fun PostDto.toCatalogThread(board: String): CatalogThread = CatalogThread(
    board = board,
    no = no,
    subject = sub?.let { PostHtmlParser.parse(it).plainText.ifBlank { null } },
    excerpt = PostHtmlParser.parse(com),
    thumbnailUrl = tim?.let { Urls.thumbnail(board, it) },
    replyCount = replies ?: 0,
    imageCount = images ?: 0,
    lastModified = last_modified ?: time,
    sticky = sticky == 1,
    closed = closed == 1,
)

fun PostDto.toThreadPost(board: String): ThreadPost {
    val body = PostHtmlParser.parse(com)
    return ThreadPost(
        board = board,
        no = no,
        isOp = resto == 0L,
        name = name ?: "Anonymous",
        tripcode = trip,
        capcode = capcode,
        posterId = id,
        countryCode = country,
        countryName = country_name ?: flag_name,
        timeSeconds = time,
        subject = sub?.let { PostHtmlParser.parse(it).plainText.ifBlank { null } },
        body = body,
        media = toPostMedia(board),
        quotedPostNos = body.segments.mapNotNull {
            (it.annotation as? PostAnnotation.QuotelinkSameThread)?.postNo
        }.distinct(),
    )
}

fun PostDto.toPostMedia(board: String): PostMedia? {
    if (filedeleted == 1) {
        return PostMedia.Deleted(displayName = "${filename ?: "deleted"}${ext ?: ""}")
    }
    val t = tim ?: return null
    val e = ext ?: return null
    val sound = SoundPost.parse(filename ?: t.toString())
    return PostMedia.Present(
        MediaItem(
            postNo = no,
            filename = sound.name,
            ext = e,
            soundUrl = sound.url,
            sizeBytes = fsize ?: 0,
            width = w ?: 0,
            height = h ?: 0,
            thumbnailUrl = Urls.thumbnail(board, t),
            fullUrl = Urls.fullMedia(board, t, e),
            spoiler = spoiler == 1,
        )
    )
}

fun buildThreadDetails(
    board: String,
    threadNo: Long,
    posts: List<ThreadPost>,
    archived: Boolean = false,
    closed: Boolean = false,
): ThreadDetails {
    return ThreadDetails(
        board = board,
        threadNo = threadNo,
        posts = posts,
        archived = archived,
        closed = closed,
        backlinks = backlinksOf(posts),
    )
}

fun BookmarkEntity.toDomain() = Bookmark(
    board = board,
    threadNo = threadNo,
    subject = subject,
    opExcerpt = opExcerpt,
    thumbnailUrl = thumbnailUrl,
    replyCount = replyCount,
    imageCount = imageCount,
    bookmarkedAt = bookmarkedAt,
    lastCheckedAt = lastCheckedAt,
    lastSeenPostNo = lastSeenPostNo,
    state = runCatching { BookmarkState.valueOf(state) }.getOrDefault(BookmarkState.UNKNOWN),
    readUpTo = readUpTo,
    postNos = decodePostNos(postNos),
    pinned = pinned,
    lastActivityAt = lastActivityAt,
)

fun Bookmark.toEntity() = BookmarkEntity(
    board = board,
    threadNo = threadNo,
    subject = subject,
    opExcerpt = opExcerpt,
    thumbnailUrl = thumbnailUrl,
    replyCount = replyCount,
    imageCount = imageCount,
    bookmarkedAt = bookmarkedAt,
    lastCheckedAt = lastCheckedAt,
    lastSeenPostNo = lastSeenPostNo,
    state = state.name,
    readUpTo = readUpTo,
    postNos = encodePostNos(postNos),
    pinned = pinned,
    lastActivityAt = lastActivityAt,
)

fun encodePostNos(nos: List<Long>): String = nos.joinToString(",")

fun decodePostNos(raw: String): List<Long> =
    if (raw.isEmpty()) emptyList() else raw.split(',').mapNotNull { it.toLongOrNull() }

fun HistoryEntity.toDomain() = HistoryEntry(
    board = board,
    threadNo = threadNo,
    subject = subject,
    opExcerpt = opExcerpt,
    thumbnailUrl = thumbnailUrl,
    viewedAt = viewedAt,
    lastScrollPostNo = lastScrollPostNo,
)

fun HistoryEntry.toEntity() = HistoryEntity(
    board = board,
    threadNo = threadNo,
    subject = subject,
    opExcerpt = opExcerpt,
    thumbnailUrl = thumbnailUrl,
    viewedAt = viewedAt,
    lastScrollPostNo = lastScrollPostNo,
)

package dev.stan.yotsuba.di

import dev.stan.yotsuba.domain.model.ArchiveSource
import dev.stan.yotsuba.domain.model.Board
import dev.stan.yotsuba.domain.model.BoardCategory
import dev.stan.yotsuba.domain.model.Bookmark
import dev.stan.yotsuba.domain.model.BookmarkState
import dev.stan.yotsuba.domain.model.CatalogThread
import dev.stan.yotsuba.domain.model.HistoryEntry
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.PostAnnotation
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.PostSegment
import dev.stan.yotsuba.domain.model.PostStyle
import dev.stan.yotsuba.domain.model.PostText
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultLocation

/**
 * Deterministic seed data every UI test drives against. No network, no Room.
 *
 * Three boards, six threads. /g/ is the ordinary one: the seeded thread carries every
 * kind of post body the thread screen renders (image, spoiler, quote, link, cross-thread
 * quote, deadlink, greentext with a text spoiler), a sticky and a closed sibling, and an
 * archived thread that only the archive fallback answers. /v/ has poster IDs, webm audio
 * and a video thread with a sound post. /b/ is the NSFW board.
 *
 * Every constant is a string a test can wait for on screen, so name them by what they say.
 */
object TestSeed {
    const val BOARD = "g"
    const val BOARD_TITLE = "Technology"
    const val VIDEO_BOARD = "v"
    const val VIDEO_BOARD_TITLE = "Video Games"
    const val NSFW_BOARD = "b"
    const val NSFW_BOARD_TITLE = "Random"

    const val THREAD_NO = 1000L
    const val THREAD_SUBJECT = "Yotsuba test thread"
    const val OP_TEXT = "This is the seeded OP post body"
    const val REPLY_TEXT = "This is the seeded reply with an image"
    const val MEDIA_FILENAME = "seeded_image"
    const val SPOILER_REPLY_TEXT = "Reply hiding a surprise picture"
    const val SPOILER_FILENAME = "spoiler_image"
    const val QUOTE_REPLY_TEXT = "Replying to the OP with a quote"
    const val LINK_REPLY_TEXT = "Look at this page"
    const val LINK_URL = "https://example.com/page"
    const val CROSS_QUOTE_REPLY_TEXT = "points at the sticky"
    const val DEADLINK_REPLY_TEXT = "is gone for good"
    const val DEADLINK_POST_NO = 999L
    const val GREENTEXT_LINE = ">implying the seed is green"
    const val SPOILER_TEXT = "hidden words in the seed"

    const val STICKY_THREAD_NO = 2000L
    const val STICKY_SUBJECT = "Second seeded thread"
    const val STICKY_OP_TEXT = "Sticky OP text"
    const val STICKY_REPLY_TEXT = "Reply in the sticky"
    const val CLOSED_THREAD_NO = 3000L
    const val CLOSED_SUBJECT = "Closed seeded thread"
    const val CLOSED_OP_TEXT = "Nothing more will be posted here"
    const val ARCHIVED_THREAD_NO = 5000L
    const val ARCHIVED_SUBJECT = "Archived seeded thread"
    const val ARCHIVED_OP_TEXT = "Only the archive still has this"

    const val VIDEO_THREAD_NO = 4000L
    const val VIDEO_SUBJECT = "Video thread"
    const val VIDEO_OP_TEXT = "The OP of the video thread"
    const val VIDEO_OP_POSTER_ID = "Ab12Cd34"
    const val VIDEO_SAME_POSTER_TEXT = "Same poster again"
    const val VIDEO_REPLY_TEXT = "Reply with a video"
    const val VIDEO_REPLY_POSTER_ID = "Zz99Yy88"
    const val VIDEO_FILENAME = "seeded_clip"
    const val SOUND_REPLY_TEXT = "Reply with a sound post"
    const val SOUND_FILENAME = "seeded_soundpost"

    const val NSFW_THREAD_NO = 6000L
    const val NSFW_SUBJECT = "Random board thread"
    const val NSFW_OP_TEXT = "The OP on the NSFW board"

    private fun text(s: String) = PostText(listOf(PostSegment(s)))

    fun board(
        code: String,
        title: String,
        category: BoardCategory = BoardCategory.INTERESTS,
        worksafe: Boolean = true,
        userIds: Boolean = false,
        spoilers: Boolean = false,
        webmAudio: Boolean = false,
    ) = Board(
        code = code,
        title = title,
        description = "$title board",
        worksafe = worksafe,
        category = category,
        userIds = userIds,
        countryFlags = false,
        boardFlags = false,
        spoilers = spoilers,
        webmAudio = webmAudio,
        codeTags = true,
        mathTags = false,
        sjisTags = false,
        textOnly = false,
    )

    val board = board(BOARD, BOARD_TITLE)
    val videoBoard = board(VIDEO_BOARD, VIDEO_BOARD_TITLE, BoardCategory.VIDEO_GAMES, userIds = true, spoilers = true, webmAudio = true)
    val nsfwBoard = board(NSFW_BOARD, NSFW_BOARD_TITLE, BoardCategory.ADULT, worksafe = false)
    val boards = listOf(board, videoBoard, nsfwBoard)

    fun catalogThread(
        board: String,
        no: Long,
        subject: String?,
        excerpt: String,
        replyCount: Int = 1,
        imageCount: Int = 0,
        sticky: Boolean = false,
        closed: Boolean = false,
        lastModified: Long = 1_700_000_000L,
        lastReplyNos: List<Long> = emptyList(),
    ) = CatalogThread(
        board = board,
        no = no,
        subject = subject,
        excerpt = text(excerpt),
        thumbnailUrl = null,
        replyCount = replyCount,
        imageCount = imageCount,
        lastModified = lastModified,
        sticky = sticky,
        closed = closed,
        lastReplyNos = lastReplyNos,
    )

    val catalogThread = catalogThread(BOARD, THREAD_NO, THREAD_SUBJECT, OP_TEXT, replyCount = 7, imageCount = 2, lastModified = 1_700_000_300L)
    val stickyCatalogThread = catalogThread(BOARD, STICKY_THREAD_NO, STICKY_SUBJECT, STICKY_OP_TEXT, sticky = true, lastModified = 1_700_000_200L)
    val closedCatalogThread = catalogThread(BOARD, CLOSED_THREAD_NO, CLOSED_SUBJECT, CLOSED_OP_TEXT, replyCount = 0, closed = true, lastModified = 1_700_000_100L)
    val videoCatalogThread = catalogThread(VIDEO_BOARD, VIDEO_THREAD_NO, VIDEO_SUBJECT, VIDEO_OP_TEXT, replyCount = 3, imageCount = 2)
    val nsfwCatalogThread = catalogThread(NSFW_BOARD, NSFW_THREAD_NO, NSFW_SUBJECT, NSFW_OP_TEXT)

    /** Catalog order is bump order: the seeded thread first, the closed one last. */
    val catalogs: Map<String, List<CatalogThread>> = mapOf(
        BOARD to listOf(catalogThread, stickyCatalogThread, closedCatalogThread),
        VIDEO_BOARD to listOf(videoCatalogThread),
        NSFW_BOARD to listOf(nsfwCatalogThread),
    )

    fun mediaItem(
        postNo: Long,
        filename: String,
        ext: String = ".png",
        spoiler: Boolean = false,
        soundUrl: String? = null,
        sizeBytes: Long = 12_345L,
        width: Int = 800,
        height: Int = 600,
        md5: String? = null,
    ) = MediaItem(
        postNo = postNo,
        filename = filename,
        ext = ext,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        thumbnailUrl = "https://example.invalid/${filename}_thumb.jpg",
        fullUrl = "https://example.invalid/$filename$ext",
        spoiler = spoiler,
        soundUrl = soundUrl,
        md5 = md5,
    )

    val mediaItem = mediaItem(THREAD_NO + 1, MEDIA_FILENAME, md5 = "seededmd5==")
    val spoilerMediaItem = mediaItem(THREAD_NO + 2, SPOILER_FILENAME, spoiler = true, sizeBytes = 6_789L, width = 640, height = 480)
    val videoItem = mediaItem(VIDEO_THREAD_NO + 2, VIDEO_FILENAME, ext = ".webm", sizeBytes = 2_345_678L, width = 1280, height = 720)
    val soundItem = mediaItem(VIDEO_THREAD_NO + 3, SOUND_FILENAME, ext = ".webm", soundUrl = "https://example.invalid/sound.ogg", sizeBytes = 1_234_567L)

    fun post(
        board: String,
        no: Long,
        body: PostText,
        isOp: Boolean = false,
        subject: String? = null,
        media: PostMedia? = null,
        posterId: String? = null,
        quotedPostNos: List<Long> = body.quotedPostNos,
        timeSeconds: Long = 1_700_000_000L + (no % 1000) * 60,
    ) = ThreadPost(
        board = board,
        no = no,
        isOp = isOp,
        name = "Anonymous",
        tripcode = null,
        capcode = null,
        posterId = posterId,
        countryCode = null,
        countryName = null,
        timeSeconds = timeSeconds,
        subject = subject,
        body = body,
        media = media,
        quotedPostNos = quotedPostNos,
    )

    fun post(board: String, no: Long, body: String, isOp: Boolean = false, subject: String? = null, media: PostMedia? = null, posterId: String? = null) =
        post(board, no, text(body), isOp, subject, media, posterId)

    val threadDetails = ThreadDetails(
        board = BOARD,
        threadNo = THREAD_NO,
        posts = listOf(
            post(BOARD, THREAD_NO, OP_TEXT, isOp = true, subject = THREAD_SUBJECT),
            post(BOARD, THREAD_NO + 1, REPLY_TEXT, media = PostMedia.Present(mediaItem)),
            post(BOARD, THREAD_NO + 2, SPOILER_REPLY_TEXT, media = PostMedia.Present(spoilerMediaItem)),
            post(
                BOARD, THREAD_NO + 3,
                PostText(
                    listOf(
                        PostSegment(">>$THREAD_NO", annotation = PostAnnotation.QuotelinkSameThread(THREAD_NO)),
                        PostSegment("\n$QUOTE_REPLY_TEXT"),
                    ),
                ),
            ),
            post(
                BOARD, THREAD_NO + 4,
                PostText(
                    listOf(
                        PostSegment("$LINK_REPLY_TEXT "),
                        PostSegment(LINK_URL, annotation = PostAnnotation.Link(LINK_URL)),
                    ),
                ),
            ),
            post(
                BOARD, THREAD_NO + 5,
                PostText(
                    listOf(
                        PostSegment(">>>/$BOARD/$STICKY_THREAD_NO", annotation = PostAnnotation.QuotelinkCrossThread(BOARD, STICKY_THREAD_NO, null)),
                        PostSegment(" $CROSS_QUOTE_REPLY_TEXT"),
                    ),
                ),
            ),
            post(
                BOARD, THREAD_NO + 6,
                PostText(
                    listOf(
                        PostSegment(">>$DEADLINK_POST_NO", styles = setOf(PostStyle.DEADLINK), annotation = PostAnnotation.Deadlink(DEADLINK_POST_NO)),
                        PostSegment(" $DEADLINK_REPLY_TEXT"),
                    ),
                ),
            ),
            post(
                BOARD, THREAD_NO + 7,
                PostText(
                    listOf(
                        PostSegment(GREENTEXT_LINE, styles = setOf(PostStyle.GREENTEXT)),
                        PostSegment("\n"),
                        PostSegment(SPOILER_TEXT, styles = setOf(PostStyle.SPOILER), annotation = PostAnnotation.Spoiler(0), spoilerId = 0),
                    ),
                ),
            ),
        ),
        archived = false,
        closed = false,
        backlinks = mapOf(THREAD_NO to listOf(THREAD_NO + 3)),
    )

    val stickyThreadDetails = ThreadDetails(
        board = BOARD,
        threadNo = STICKY_THREAD_NO,
        posts = listOf(
            post(BOARD, STICKY_THREAD_NO, STICKY_OP_TEXT, isOp = true, subject = STICKY_SUBJECT),
            post(BOARD, STICKY_THREAD_NO + 1, STICKY_REPLY_TEXT),
        ),
        archived = false,
        closed = false,
        backlinks = emptyMap(),
        sticky = true,
    )

    val closedThreadDetails = ThreadDetails(
        board = BOARD,
        threadNo = CLOSED_THREAD_NO,
        posts = listOf(post(BOARD, CLOSED_THREAD_NO, CLOSED_OP_TEXT, isOp = true, subject = CLOSED_SUBJECT)),
        archived = false,
        closed = true,
        backlinks = emptyMap(),
    )

    /** What the archive answers for the thread 4chan no longer has. */
    val archivedThreadDetails = ThreadDetails(
        board = BOARD,
        threadNo = ARCHIVED_THREAD_NO,
        posts = listOf(post(BOARD, ARCHIVED_THREAD_NO, ARCHIVED_OP_TEXT, isOp = true, subject = ARCHIVED_SUBJECT)),
        archived = true,
        closed = true,
        backlinks = emptyMap(),
        archive = ArchiveSource.DESU,
    )

    val videoThreadDetails = ThreadDetails(
        board = VIDEO_BOARD,
        threadNo = VIDEO_THREAD_NO,
        posts = listOf(
            post(VIDEO_BOARD, VIDEO_THREAD_NO, VIDEO_OP_TEXT, isOp = true, subject = VIDEO_SUBJECT, posterId = VIDEO_OP_POSTER_ID),
            post(VIDEO_BOARD, VIDEO_THREAD_NO + 1, VIDEO_SAME_POSTER_TEXT, posterId = VIDEO_OP_POSTER_ID),
            post(VIDEO_BOARD, VIDEO_THREAD_NO + 2, VIDEO_REPLY_TEXT, media = PostMedia.Present(videoItem), posterId = VIDEO_REPLY_POSTER_ID),
            post(VIDEO_BOARD, VIDEO_THREAD_NO + 3, SOUND_REPLY_TEXT, media = PostMedia.Present(soundItem), posterId = VIDEO_REPLY_POSTER_ID),
        ),
        archived = false,
        closed = false,
        backlinks = emptyMap(),
    )

    val nsfwThreadDetails = ThreadDetails(
        board = NSFW_BOARD,
        threadNo = NSFW_THREAD_NO,
        posts = listOf(post(NSFW_BOARD, NSFW_THREAD_NO, NSFW_OP_TEXT, isOp = true, subject = NSFW_SUBJECT)),
        archived = false,
        closed = false,
        backlinks = emptyMap(),
    )

    /** Live threads, keyed by board and number. The archived one is deliberately absent. */
    val threads: Map<Pair<String, Long>, ThreadDetails> = listOf(
        threadDetails, stickyThreadDetails, closedThreadDetails, videoThreadDetails, nsfwThreadDetails,
    ).associateBy { it.board to it.threadNo }

    val archivedThreads: Map<Pair<String, Long>, ThreadDetails> =
        mapOf((BOARD to ARCHIVED_THREAD_NO) to archivedThreadDetails)

    fun bookmark(
        board: String = BOARD,
        threadNo: Long = THREAD_NO,
        subject: String? = THREAD_SUBJECT,
        opExcerpt: String = OP_TEXT,
        state: BookmarkState = BookmarkState.ALIVE,
        readUpTo: Long? = null,
        postNos: List<Long> = emptyList(),
        pinned: Boolean = false,
        bookmarkedAt: Long = 1_700_000_000_000L,
    ) = Bookmark(
        board = board,
        threadNo = threadNo,
        subject = subject,
        opExcerpt = opExcerpt,
        thumbnailUrl = null,
        replyCount = postNos.size,
        imageCount = 0,
        bookmarkedAt = bookmarkedAt,
        lastCheckedAt = null,
        state = state,
        readUpTo = readUpTo,
        postNos = postNos,
        pinned = pinned,
        lastActivityAt = bookmarkedAt,
    )

    fun historyEntry(
        board: String = BOARD,
        threadNo: Long = THREAD_NO,
        subject: String? = THREAD_SUBJECT,
        opExcerpt: String = OP_TEXT,
        viewedAt: Long = System.currentTimeMillis(),
        lastScrollPostNo: Long? = null,
    ) = HistoryEntry(
        board = board,
        threadNo = threadNo,
        subject = subject,
        opExcerpt = opExcerpt,
        thumbnailUrl = null,
        viewedAt = viewedAt,
        lastScrollPostNo = lastScrollPostNo,
    )

    /** A file already in the vault, as the explorer would list it. */
    fun vaultEntry(
        item: MediaItem = mediaItem,
        board: String = BOARD,
        threadNo: Long = THREAD_NO,
        subject: String? = THREAD_SUBJECT,
        savedAt: Long = 1_700_000_200L,
        durationMs: Long? = null,
        hasAudio: Boolean? = null,
    ) = VaultEntry(
        url = item.fullUrl,
        location = VaultLocation(board, threadNo),
        subject = subject,
        postNo = item.postNo,
        displayName = item.displayName,
        absolutePath = "/fake-vault/$board/$threadNo/${item.displayName}",
        ext = item.ext,
        sizeBytes = item.sizeBytes,
        width = item.width,
        height = item.height,
        thumbnailUrl = item.thumbnailUrl,
        savedAt = savedAt,
        durationMs = durationMs,
        hasAudio = hasAudio,
        soundUrl = item.soundUrl,
    )
}

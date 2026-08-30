package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.domain.model.Filter
import dev.stan.yotsuba.domain.model.FilterAction
import dev.stan.yotsuba.domain.model.FilterMatcher
import dev.stan.yotsuba.domain.model.FilterableFields
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.MediaSaveStatus
import dev.stan.yotsuba.domain.model.PostGraph
import dev.stan.yotsuba.domain.model.QuoteTapAction
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost

// The thread's presentation model: pure functions from (details, session, verdicts) to what
// the screen draws. Nothing here touches ViewModel state, so the row, tree and fold rules are
// testable without the flow plumbing.

/** The poster-ID filter applied; the OP always stays so the thread keeps its header. */
private fun visiblePosts(posts: List<ThreadPost>, session: Session): List<ThreadPost> {
    val id = session.filterPosterId ?: return posts
    return posts.filter { it.isOp || it.posterId == id }
}

/** Tree view indents this deep; anything deeper collapses into a "N more" row. */
const val MAX_TREE_DEPTH = 4

/** The first filter each post trips, by post number. The OP is never filtered: it is the thread. */
internal fun filterVerdicts(posts: List<ThreadPost>, matcher: FilterMatcher): Map<Long, Filter> {
    if (matcher.isEmpty) return emptyMap()
    return buildMap {
        posts.forEach { post ->
            if (post.isOp) return@forEach
            matcher.matches(FilterableFields.of(post), post.board)?.let { put(post.no, it) }
        }
    }
}

/** The row for a post the filters had a say on: nothing, a stub, or the post once opened. */
private fun filteredRow(post: ThreadPost, filter: Filter, session: Session, depth: Int): ThreadRow? = when {
    filter.action == FilterAction.HIDE -> null
    post.no in session.expandedFiltered -> ThreadRow.Post(post, depth)
    else -> ThreadRow.Filtered(post.no, filter.pattern, depth)
}

/** Linear: thread order with the new-posts divider. Tree: nested, capped, filtered. */
internal fun threadRows(details: ThreadDetails, session: Session, verdicts: Map<Long, Filter>): List<ThreadRow> =
    if (session.treeView) treeRows(details, session, verdicts)
    else linearRows(visiblePosts(details.posts, session), session, verdicts)

/** Posts in thread order, with the new-posts divider just after [newPostsAfter]'s post. */
private fun linearRows(posts: List<ThreadPost>, session: Session, verdicts: Map<Long, Filter>): List<ThreadRow> =
    buildList {
        val newPostsAfter = session.newPostsAfter
        posts.forEach { post ->
            val filter = verdicts[post.no]
            val row = if (filter == null) ThreadRow.Post(post) else filteredRow(post, filter, session, 0)
            if (row != null) add(row)
            if (newPostsAfter != null && post.no == newPostsAfter.first) {
                add(ThreadRow.NewPostsDivider(newPostsAfter.second))
            }
        }
    }

/**
 * Depth-first tree, indent capped at [MAX_TREE_DEPTH]. A capped post's deeper replies
 * follow it directly in the walk, so they fold into one "N more" row until expanded,
 * then show flattened at the cap. The ID filter applies as in the linear view; the
 * new-posts divider does not exist here since the order is no longer chronological.
 *
 * Depth counts retained ancestors only: a reply whose parent the filters removed moves
 * up under its nearest shown ancestor, or to the top level when none is left.
 */
private fun treeRows(details: ThreadDetails, session: Session, verdicts: Map<Long, Filter>): List<ThreadRow> {
    val visible = visiblePosts(details.posts, session).mapTo(HashSet()) { it.no }
    val all = PostGraph.of(details).tree()
    val parentOf = HashMap<Long, Long?>(all.size).apply { all.forEach { put(it.post.no, it.parentNo) } }
    val nodes = all.filter { it.post.no in visible && verdicts[it.post.no]?.action != FilterAction.HIDE }
    val depthOf = HashMap<Long, Int>(nodes.size)
    val depths = nodes.map { node ->
        var ancestor = node.parentNo
        while (ancestor != null && ancestor !in depthOf) ancestor = parentOf[ancestor]
        val depth = if (ancestor == null) 0 else depthOf.getValue(ancestor) + 1
        depthOf[node.post.no] = depth
        depth
    }
    val out = mutableListOf<ThreadRow>()
    var i = 0
    while (i < nodes.size) {
        val node = nodes[i]
        val depth = depths[i].coerceAtMost(MAX_TREE_DEPTH)
        val filter = verdicts[node.post.no]
        out += if (filter == null) ThreadRow.Post(node.post, depth)
        else filteredRow(node.post, filter, session, depth) ?: error("hidden posts were dropped above")
        i++
        if (depth != MAX_TREE_DEPTH) continue
        val tailStart = i
        while (i < nodes.size && depths[i] > MAX_TREE_DEPTH) i++
        val tail = nodes.subList(tailStart, i)
        if (tail.isEmpty()) continue
        if (node.post.no in session.expandedTails) {
            tail.forEach { out += ThreadRow.Post(it.post, MAX_TREE_DEPTH) }
        } else {
            out += ThreadRow.MoreReplies(node.post.no, tail.size)
        }
    }
    return out
}

/** The quotelink gesture the setting does not claim. */
internal fun QuoteTapAction.other(): QuoteTapAction = when (this) {
    QuoteTapAction.POPOVER -> QuoteTapAction.JUMP
    QuoteTapAction.JUMP -> QuoteTapAction.POPOVER
}

/** The sheet around the last post in [path]; null when the path is empty or its post is gone. */
internal fun previewSheet(details: ThreadDetails, byNo: Map<Long, ThreadPost>, path: List<Long>): PreviewSheet? {
    val focus = path.lastOrNull()?.let { byNo[it] } ?: return null
    val graph = PostGraph.of(details)
    return PreviewSheet(
        focus = focus,
        parents = graph.parentsOf(focus.no),
        replies = graph.repliesTo(focus.no),
        path = path,
    )
}

/** The OP is labelled first; a claimed OP still reads as yours. */
internal fun quoteLabels(details: ThreadDetails, claimed: Set<Long>): Map<Long, QuoteLabel> = buildMap {
    details.posts.firstOrNull { it.isOp }?.let { put(it.no, QuoteLabel.OP) }
    claimed.forEach { put(it, QuoteLabel.YOU) }
}

internal fun postStates(
    details: ThreadDetails,
    session: Session,
    saveStatuses: Map<String, MediaSaveStatus>,
    savedPaths: Map<String, String?> = emptyMap(),
    dataSaver: Boolean = false,
): Map<Long, PostUiState> {
    val revealedText = session.revealedText.groupBy({ it.first }, { it.second })
    val idCounts = details.posts.mapNotNull { it.posterId }.groupingBy { it }.eachCount()
    return details.posts.associate { post ->
        post.no to PostUiState(
            posterIdCount = post.posterId?.let { idCounts[it] } ?: 0,
            closed = post.isOp && details.closed,
            sticky = post.isOp && details.sticky,
            revealedSpoilerIds = revealedText[post.no]?.toSet().orEmpty(),
            imageSpoilerRevealed = post.no in session.revealedImages,
            backlinks = details.backlinks[post.no].orEmpty(),
            saveStatus = post.presentMedia?.fullUrl?.let { saveStatuses[it] },
            highlighted = post.no == session.highlightedPostNo,
            inlineImage = post.presentMedia?.takeIf { post.no in session.expandedImages }?.let { media ->
                InlineImage(localPath = savedPaths[media.fullUrl], dataSaver = dataSaver)
            },
        )
    }
}

/** What a tap on a post's thumbnail does. */
enum class ThumbnailTap { REVEAL_SPOILER, COLLAPSE, EXPAND, OPEN_VIEWER }

/**
 * A spoilered thumbnail reveals first. An expanded image always collapses, even if the
 * setting went off meanwhile. Otherwise the setting decides between expanding in place and
 * the viewer, and only a still image ever expands: videos, gifs and sound posts need the
 * viewer's player.
 */
internal fun thumbnailTap(
    media: MediaItem,
    postNo: Long,
    session: Session,
    settings: Settings,
): ThumbnailTap = when {
    media.spoiler && !settings.revealAllSpoilers && postNo !in session.revealedImages -> ThumbnailTap.REVEAL_SPOILER
    postNo in session.expandedImages -> ThumbnailTap.COLLAPSE
    settings.inlineImageExpansion && !media.isAnimated && media.soundUrl == null -> ThumbnailTap.EXPAND
    else -> ThumbnailTap.OPEN_VIEWER
}

/** The post at a row index, or the nearest post above a divider. */
internal fun List<ThreadRow>.postAt(index: Int): ThreadPost? =
    (0..index).reversed().firstNotNullOfOrNull { (getOrNull(it) as? ThreadRow.Post)?.post }

internal fun searchMatches(posts: List<ThreadPost>, query: String?): List<Long> =
    if (query.isNullOrBlank()) emptyList()
    else posts.filter {
        it.body.plainText.contains(query, true) || it.subject?.contains(query, true) == true
    }.map { it.no }

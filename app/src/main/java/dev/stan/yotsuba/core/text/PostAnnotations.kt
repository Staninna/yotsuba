package dev.stan.yotsuba.core.text

import kotlinx.serialization.Serializable

/** Character-level styles a segment can carry. */
@Serializable
enum class PostStyle {
    GREENTEXT, SPOILER, CODE, SJIS, MATH, BOLD, ITALIC, UNDERLINE, DEADLINK,
}

/** Typed payloads for tappable (or deliberately inert) ranges (D10/D11). */
@Serializable
sealed interface PostAnnotation {
    /** `href="#p123"` — same thread: floating preview card. */
    @Serializable
    data class QuotelinkSameThread(val postNo: Long) : PostAnnotation

    /** `href="/g/thread/456#p789"` — navigate with a scroll-to-post target. */
    @Serializable
    data class QuotelinkCrossThread(
        val board: String,
        val threadNo: Long,
        val postNo: Long?,
    ) : PostAnnotation

    /** `<span class="deadlink">` — inert styled text, no tap target. */
    @Serializable
    data object Deadlink : PostAnnotation

    /** A plain `<a href>` link; internal-vs-external routing is decided at tap time (D26). */
    @Serializable
    data class Link(val url: String) : PostAnnotation

    /** `<s>` spoiler run; [id] gives each run its own reveal state. */
    @Serializable
    data class Spoiler(val id: Int) : PostAnnotation
}

/**
 * One run of text with uniform styling and at most one annotation.
 *
 * [spoilerId] is set for every segment inside an `<s>` run, even when a nested link's
 * annotation wins [annotation] — the reveal state must not leak spoilered link text.
 */
@Serializable
data class PostSegment(
    val text: String,
    val styles: Set<PostStyle> = emptySet(),
    val annotation: PostAnnotation? = null,
    val spoilerId: Int? = null,
)

/** The parsed body of a post: raw markup never survives into [segments] (D10). */
@Serializable
data class PostText(val segments: List<PostSegment>) {
    val plainText: String get() = segments.joinToString("") { it.text }

    /** Same-thread quotelinks in order of first appearance. */
    val quotedPostNos: List<Long>
        get() = segments.mapNotNull { (it.annotation as? PostAnnotation.QuotelinkSameThread)?.postNo }.distinct()
    companion object {
        val Empty = PostText(emptyList())
    }
}

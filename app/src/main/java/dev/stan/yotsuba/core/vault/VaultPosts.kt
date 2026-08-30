package dev.stan.yotsuba.core.vault

import dev.stan.yotsuba.domain.model.PostAnnotation
import dev.stan.yotsuba.domain.model.PostText
import kotlinx.serialization.Serializable

/**
 * `posts.json`: the conversation around the saved media, as text.
 *
 * Written beside `meta.json` rather than inside it, because meta.json is read-modify-written on
 * every single file save, and a few hundred posts folded into it would be rewritten each
 * time. Both files are plain JSON next to the media, so the vault stays self-describing.
 *
 * Never holds bytes. A post's attachment is recorded by URL and name, so a file saved
 * later can be matched back to the post it came from.
 */
@Serializable
data class VaultThreadPosts(
    val board: String,
    val threadNo: Long,
    val posts: List<VaultPostMeta> = emptyList(),
) {
    /** Later saves widen the snapshot; a post already recorded is replaced, not duplicated. */
    fun mergedWith(incoming: List<VaultPostMeta>): VaultThreadPosts {
        if (incoming.isEmpty()) return this
        val byNo = posts.associateByTo(LinkedHashMap()) { it.no }
        incoming.forEach { byNo[it.no] = it }
        return copy(posts = byNo.values.sortedBy { it.no })
    }
}

@Serializable
data class VaultPostMeta(
    val no: Long,
    val isOp: Boolean = false,
    val name: String? = null,
    val tripcode: String? = null,
    val capcode: String? = null,
    val posterId: String? = null,
    val countryCode: String? = null,
    val countryName: String? = null,
    val timeSeconds: Long = 0,
    val subject: String? = null,
    /** Parsed segments, not raw markup, so greentext and quotelinks render as they did live. */
    val body: PostText = PostText.Empty,
    val quotedPostNos: List<Long> = emptyList(),
    /** The attachment this post carried, if any. Recorded, never downloaded by itself. */
    val file: VaultPostFile? = null,
)

@Serializable
data class VaultPostFile(
    val filename: String,
    val ext: String,
    val url: String,
    val thumbnailUrl: String,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0,
    val spoiler: Boolean = false,
)

/**
 * How a merged-in conversation is renumbered so it cannot overwrite the target's posts.
 *
 * Two imported threads both number their synthetic posts 1..n, so folding one into the
 * other by post number would replace the target's conversation with the source's. When
 * any source number is already [taken] by the target, every source post moves past the
 * target's highest number, keeping its order; a remote thread's numbers are unique on the
 * board and never collide, so they pass through untouched.
 */
object VaultPostRenumbering {
    /**
     * Old source number to new number; empty when nothing collides and nothing moves.
     * [sourceNos] are the source's post and file numbers; a collision is one of them
     * already present as a post in the target. [targetNos] are every number the target
     * uses, posts and files alike, so the offset clears all of them.
     */
    fun plan(sourceNos: Collection<Long>, targetPostNos: Collection<Long>, targetNos: Collection<Long>): Map<Long, Long> {
        if (sourceNos.none { it in targetPostNos }) return emptyMap()
        val offset = (targetPostNos + targetNos).maxOrNull() ?: 0L
        return sourceNos.distinct().associateWith { it + offset }
    }

    /**
     * [post] under its new number, with every internal reply reference following. A
     * renumbered post is never an OP: the target already has one.
     */
    fun apply(post: VaultPostMeta, remap: Map<Long, Long>): VaultPostMeta {
        if (remap.isEmpty()) return post
        fun Long.moved() = remap[this] ?: this
        return post.copy(
            no = post.no.moved(),
            isOp = false,
            quotedPostNos = post.quotedPostNos.map { it.moved() },
            body = PostText(
                post.body.segments.map { segment ->
                    val quote = segment.annotation as? PostAnnotation.QuotelinkSameThread
                    if (quote == null || quote.postNo !in remap) {
                        segment
                    } else {
                        val to = quote.postNo.moved()
                        segment.copy(
                            text = if (segment.text == ">>${quote.postNo}") ">>$to" else segment.text,
                            annotation = PostAnnotation.QuotelinkSameThread(to),
                        )
                    }
                },
            ),
        )
    }
}

object VaultPostsCodec {
    fun encode(posts: VaultThreadPosts): String = SidecarJson.encode(posts)

    fun decode(text: String): VaultThreadPosts? = SidecarJson.decode(text)
}

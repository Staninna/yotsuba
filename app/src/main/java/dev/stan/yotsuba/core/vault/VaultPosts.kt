package dev.stan.yotsuba.core.vault

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

object VaultPostsCodec {
    fun encode(posts: VaultThreadPosts): String = SidecarJson.encode(posts)

    fun decode(text: String): VaultThreadPosts? = SidecarJson.decode(text)
}

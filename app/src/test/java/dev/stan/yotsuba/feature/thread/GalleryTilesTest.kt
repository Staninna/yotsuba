package dev.stan.yotsuba.feature.thread

import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.feature.thread.components.GalleryFilter
import dev.stan.yotsuba.feature.thread.components.galleryTiles
import org.junit.Assert.assertEquals
import org.junit.Test

/** The gallery shows a reposted file once, on its first post, with the repost count. */
class GalleryTilesTest {

    private fun withMd5(no: Long, md5: String?) = ThreadEnv.postWithMedia(no).let { post ->
        post.copy(media = PostMedia.Present((post.media as PostMedia.Present).item.copy(md5 = md5)))
    }

    @Test fun `reposts fold into the first post and files without an MD5 stand alone`() {
        val posts = listOf(
            withMd5(100, "aaa"), withMd5(101, "bbb"), withMd5(102, "aaa"),
            withMd5(103, null), withMd5(104, null), withMd5(105, "aaa"),
            ThreadEnv.post(106), // no media; the caller normally filters these out already
        )
        val tiles = galleryTiles(posts, GalleryFilter.ALL, boardAllowsAudio = false)
        assertEquals(listOf(100L, 101L, 103L, 104L), tiles.map { it.post.no })
        assertEquals(listOf(3, 1, 1, 1), tiles.map { it.copies })
    }
}

package dev.stan.yotsuba.data

import dev.stan.yotsuba.core.network.dto.PostDto
import dev.stan.yotsuba.data.repository.buildThreadDetails
import dev.stan.yotsuba.data.repository.toPostMedia
import dev.stan.yotsuba.data.repository.toThreadPost
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.PostMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {

    // toPostMedia

    @Test fun `filedeleted maps to Deleted with filename and ext`() {
        val media = PostDto(no = 1, filedeleted = 1, filename = "cat", ext = ".jpg").toPostMedia("g")
        assertEquals(PostMedia.Deleted(displayName = "cat.jpg"), media)
    }

    @Test fun `filedeleted with no filename falls back to deleted`() {
        assertEquals(PostMedia.Deleted("deleted"), PostDto(no = 1, filedeleted = 1).toPostMedia("g"))
    }

    @Test fun `no tim or ext maps to null`() {
        assertNull(PostDto(no = 1).toPostMedia("g"))
        assertNull(PostDto(no = 1, tim = 123L).toPostMedia("g"))
        assertNull(PostDto(no = 1, ext = ".png").toPostMedia("g"))
    }

    @Test fun `normal file maps to Present with built urls`() {
        val dto = PostDto(
            no = 7, tim = 1700000000000L, filename = "pic", ext = ".png",
            fsize = 1234, w = 640, h = 480, spoiler = 1,
        )
        val media = dto.toPostMedia("g") as PostMedia.Present
        val item = media.item
        assertEquals(7L, item.postNo)
        assertEquals("pic", item.filename)
        assertEquals(".png", item.ext)
        assertEquals(1234L, item.sizeBytes)
        assertEquals(640, item.width)
        assertEquals(480, item.height)
        assertEquals("https://i.4cdn.org/g/1700000000000s.jpg", item.thumbnailUrl)
        assertEquals("https://i.4cdn.org/g/1700000000000.png", item.fullUrl)
        assertTrue(item.spoiler)
    }

    @Test fun `missing filename falls back to tim, missing sizes default`() {
        val item = (PostDto(no = 1, tim = 42L, ext = ".gif").toPostMedia("g") as PostMedia.Present).item
        assertEquals("42", item.filename)
        assertEquals(0L, item.sizeBytes)
        assertEquals(0, item.width)
        assertEquals(0, item.height)
        assertFalse(item.spoiler)
    }

    // toThreadPost

    @Test fun `quotedPostNos extracted from same-thread quotelinks, distinct`() {
        val dto = PostDto(
            no = 3, resto = 1,
            com = "<a href=\"#p1\" class=\"quotelink\">&gt;&gt;1</a> " +
                "<a href=\"#p2\" class=\"quotelink\">&gt;&gt;2</a> " +
                "<a href=\"#p1\" class=\"quotelink\">&gt;&gt;1</a> " +
                "<a href=\"/a/thread/9#p9\" class=\"quotelink\">&gt;&gt;9</a>",
        )
        assertEquals(listOf(1L, 2L), dto.toThreadPost("g").quotedPostNos)
    }

    @Test fun `toThreadPost basics`() {
        val post = PostDto(no = 1, resto = 0, sub = "hello", time = 99).toThreadPost("g")
        assertTrue(post.isOp)
        assertEquals("Anonymous", post.name)
        assertEquals("hello", post.subject)
        assertEquals(99L, post.timeSeconds)
        assertNull(post.media)
    }

    // buildThreadDetails

    @Test fun `buildThreadDetails builds backlinks from quotedPostNos`() {
        val posts = listOf(
            PostDto(no = 1, resto = 0).toThreadPost("g"),
            PostDto(no = 2, resto = 1, com = "<a href=\"#p1\" class=\"quotelink\">&gt;&gt;1</a>").toThreadPost("g"),
            PostDto(no = 3, resto = 1, com = "<a href=\"#p1\" class=\"quotelink\">&gt;&gt;1</a>" +
                "<a href=\"#p2\" class=\"quotelink\">&gt;&gt;2</a>").toThreadPost("g"),
        )
        val details = buildThreadDetails("g", 1, posts, archived = true, closed = false)
        assertEquals(listOf(2L, 3L), details.backlinks[1L])
        assertEquals(listOf(3L), details.backlinks[2L])
        assertNull(details.backlinks[3L])
        assertTrue(details.archived)
        assertFalse(details.closed)
    }

    // MediaItem accessors

    private fun item(ext: String) = MediaItem(
        postNo = 1, filename = "clip", ext = ext, sizeBytes = 0, width = 0, height = 0,
        thumbnailUrl = "t", fullUrl = "f", spoiler = false,
    )

    @Test fun `isVideo and isAnimated by extension`() {
        assertTrue(item(".webm").isVideo)
        assertTrue(item(".mp4").isVideo)
        assertFalse(item(".gif").isVideo)
        assertTrue(item(".gif").isAnimated)
        assertTrue(item(".webm").isAnimated)
        assertFalse(item(".jpg").isAnimated)
    }

    @Test fun `displayName joins filename and ext`() {
        assertEquals("clip.webm", item(".webm").displayName)
    }
}

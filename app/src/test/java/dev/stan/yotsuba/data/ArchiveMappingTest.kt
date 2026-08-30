package dev.stan.yotsuba.data

import dev.stan.yotsuba.core.network.dto.parseFoolFuukaThread
import dev.stan.yotsuba.core.text.archiveCommentToHtml
import dev.stan.yotsuba.data.repository.toThreadDetails
import dev.stan.yotsuba.domain.model.ArchiveSource
import dev.stan.yotsuba.domain.model.PostAnnotation
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.PostStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String): JsonObject = json.decodeFromString(
        JsonObject.serializer(),
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!.bufferedReader().readText(),
    )

    @Test fun `an error reply parses to nothing`() {
        assertNull(parseFoolFuukaThread(fixture("foolfuuka_error.json")))
    }

    @Test fun `a foolfuuka thread maps to posts in number order`() {
        val dto = parseFoolFuukaThread(fixture("foolfuuka_thread.json"))!!
        val details = dto.toThreadDetails("a", ArchiveSource.DESU)

        assertEquals(5000L, details.threadNo)
        assertTrue(details.archived)
        assertEquals(ArchiveSource.DESU, details.archive)
        assertEquals(listOf(5000L, 5001L, 5002L), details.posts.map { it.no })

        val op = details.posts[0]
        assertTrue(op.isOp)
        assertEquals("Archived thread", op.subject)
        assertEquals("AbCd1234", op.posterId)
        assertEquals("NL", op.countryCode)
        assertNull(op.capcode)
        assertEquals(1_700_000_000L, op.timeSeconds)
        val media = (op.media as PostMedia.Present).item
        assertEquals("kitten", media.filename)
        assertEquals(".jpg", media.ext)
        assertEquals(800, media.width)
        assertEquals(12_345L, media.sizeBytes)
        assertEquals("https://desu-usergeneratedcontent.xyz/a/image/1700/00/1700000000000.jpg", media.fullUrl)
        assertEquals("https://desu-usergeneratedcontent.xyz/a/thumb/1700/00/1700000000000s.jpg", media.thumbnailUrl)

        val mod = details.posts[1]
        assertEquals("M", mod.capcode)
        assertEquals(PostMedia.Deleted("gone.png"), mod.media)

        val reply = details.posts[2]
        assertEquals(listOf(5000L), reply.quotedPostNos)
        assertEquals(listOf(5002L), details.backlinks[5000L])
    }

    @Test fun `the raw comment becomes 4chan markup with quotelinks and greentext`() {
        val html = archiveCommentToHtml(">>4999\n>green line\nplain & <b>bold</b> line")
        assertEquals(
            """<a href="#p4999" class="quotelink">&gt;&gt;4999</a><br>""" +
                """<span class="quote">&gt;green line</span><br>""" +
                """plain &amp; &lt;b&gt;bold&lt;/b&gt; line""",
            html,
        )
        val text = dev.stan.yotsuba.core.text.PostHtmlParser.parse(html)
        assertEquals("plain & <b>bold</b> line", text.plainText.lines().last())
        assertTrue(text.segments.any { it.annotation == PostAnnotation.QuotelinkSameThread(4999) })
        assertTrue(text.segments.any { PostStyle.GREENTEXT in it.styles })
        assertNull(archiveCommentToHtml(null))
    }
}

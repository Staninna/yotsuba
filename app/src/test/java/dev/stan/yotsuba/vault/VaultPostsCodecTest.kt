package dev.stan.yotsuba.vault

import dev.stan.yotsuba.core.vault.VaultPostMeta
import dev.stan.yotsuba.core.vault.VaultPostsCodec
import dev.stan.yotsuba.core.vault.VaultThreadPosts
import dev.stan.yotsuba.data.repository.toThreadPost
import dev.stan.yotsuba.data.repository.toVaultMeta
import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.PostAnnotation
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.PostSegment
import dev.stan.yotsuba.domain.model.PostStyle
import dev.stan.yotsuba.domain.model.PostText
import dev.stan.yotsuba.domain.model.ThreadPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VaultPostsCodecTest {

    private val richBody = PostText(
        listOf(
            PostSegment(">>123", annotation = PostAnnotation.QuotelinkSameThread(123)),
            PostSegment("\n"),
            PostSegment(">implying", styles = setOf(PostStyle.GREENTEXT)),
            PostSegment("hidden", styles = setOf(PostStyle.SPOILER), spoilerId = 1),
        ),
    )

    private fun post(no: Long, withMedia: Boolean = false) = ThreadPost(
        board = "g", no = no, isOp = no == 1L, name = "Anonymous", tripcode = "!!abc",
        capcode = null, posterId = "Ab12Cd34", countryCode = "NL", countryName = "Netherlands",
        timeSeconds = 1_700_000_000, subject = if (no == 1L) "the subject" else null,
        body = richBody,
        media = if (withMedia) {
            PostMedia.Present(
                MediaItem(
                    postNo = no, filename = "pic", ext = ".jpg", sizeBytes = 4096,
                    width = 800, height = 600,
                    thumbnailUrl = "https://i.4cdn.org/g/${no}s.jpg",
                    fullUrl = "https://i.4cdn.org/g/$no.jpg",
                    spoiler = true,
                ),
            )
        } else {
            null
        },
        quotedPostNos = listOf(123),
    )

    @Test fun `a post survives the round trip with its markup intact`() {
        val original = post(200, withMedia = true)
        val restored = original.toVaultMeta().toThreadPost("g")

        assertEquals(original, restored)
        // Greentext, quotelinks and spoiler runs are what make it "mirror the live version".
        assertEquals(richBody.segments, restored.body.segments)
        assertEquals("https://i.4cdn.org/g/200.jpg", restored.presentMedia?.fullUrl)
    }

    @Test fun `encoding and decoding preserves the whole snapshot`() {
        val snapshot = VaultThreadPosts("g", 100, listOf(post(1).toVaultMeta(), post(2, true).toVaultMeta()))
        assertEquals(snapshot, VaultPostsCodec.decode(VaultPostsCodec.encode(snapshot)))
    }

    @Test fun `merging widens the snapshot and replaces rather than duplicates`() {
        val first = VaultThreadPosts("g", 100, listOf(post(1).toVaultMeta()))
        val merged = first.mergedWith(listOf(post(1).toVaultMeta(), post(3).toVaultMeta()))

        assertEquals(listOf(1L, 3L), merged.posts.map { it.no })
        assertEquals(merged, merged.mergedWith(emptyList()))
    }

    @Test fun `merged posts come back in post order however they arrived`() {
        val out = VaultThreadPosts("g", 100)
            .mergedWith(listOf(post(9).toVaultMeta()))
            .mergedWith(listOf(post(2).toVaultMeta(), post(5).toVaultMeta()))
        assertEquals(listOf(2L, 5L, 9L), out.posts.map { it.no })
    }

    @Test fun `a sidecar written by a newer build still decodes`() {
        val json = """{"board":"g","threadNo":100,"posts":[{"no":1,"somethingNew":true}]}"""
        assertEquals(listOf(1L), VaultPostsCodec.decode(json)?.posts?.map { it.no })
    }

    @Test fun `unparseable json is null rather than a crash`() {
        assertNull(VaultPostsCodec.decode("not json at all"))
    }

    @Test fun `a post without an attachment records no file`() {
        assertNull(post(7).toVaultMeta().file)
        assertNull(VaultPostMeta(no = 7).toThreadPost("g").presentMedia)
    }
}

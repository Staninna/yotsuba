package dev.stan.yotsuba.vault

import dev.stan.yotsuba.domain.model.MediaItem
import dev.stan.yotsuba.domain.model.PostMedia
import dev.stan.yotsuba.domain.model.PostText
import dev.stan.yotsuba.domain.model.ThreadDetails
import dev.stan.yotsuba.domain.model.ThreadPost
import dev.stan.yotsuba.domain.model.VaultEntry
import dev.stan.yotsuba.domain.model.VaultLocation
import dev.stan.yotsuba.domain.model.redownloadSources
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which missing files a thread, live or archived, can give back, and from where. */
class VaultRedownloadTest {

    private fun post(no: Long, media: PostMedia?) = ThreadPost(
        board = "g", no = no, isOp = no == 1L, name = "Anonymous", tripcode = null, capcode = null,
        posterId = null, countryCode = null, countryName = null, timeSeconds = no, subject = null,
        body = PostText(emptyList()), media = media, quotedPostNos = emptyList(),
    )

    private fun present(url: String) = PostMedia.Present(
        MediaItem(
            postNo = 0, filename = "f", ext = ".jpg", sizeBytes = 1, width = 1, height = 1,
            thumbnailUrl = "$url.s", fullUrl = url, spoiler = false,
        ),
    )

    private fun missing(url: String, postNo: Long?) = VaultEntry(
        url = url, location = VaultLocation("g", 1), subject = null, postNo = postNo,
        displayName = url.substringAfterLast('/'), absolutePath = "", ext = ".jpg", sizeBytes = null,
        width = null, height = null, thumbnailUrl = null, savedAt = 0,
    )

    @Test
    fun `a post's current media URL is the source, whichever host carries it now`() {
        val thread = ThreadDetails(
            board = "g", threadNo = 1, archived = false, closed = false, backlinks = emptyMap(),
            posts = listOf(
                post(1, null),
                post(2, present("https://desuarchive.org/g/2.jpg")),
                post(3, PostMedia.Deleted("3.jpg")),
                post(5, present("https://i.4cdn.org/g/5.jpg")),
            ),
        )
        val byPost = missing("https://i.4cdn.org/g/2.jpg", postNo = 2)
        val deleted = missing("https://i.4cdn.org/g/3.jpg", postNo = 3)
        val absent = missing("https://i.4cdn.org/g/4.jpg", postNo = 4)
        val byUrl = missing("https://i.4cdn.org/g/5.jpg", postNo = null)

        val sources = redownloadSources(listOf(byPost, deleted, absent, byUrl), thread)

        assertEquals(
            mapOf(
                byPost to "https://desuarchive.org/g/2.jpg",
                deleted to null,
                absent to null,
                byUrl to "https://i.4cdn.org/g/5.jpg",
            ),
            sources,
        )
    }
}

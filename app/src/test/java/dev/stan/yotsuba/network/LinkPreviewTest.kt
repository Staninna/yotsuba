package dev.stan.yotsuba.network

import dev.stan.yotsuba.core.network.LinkPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPreviewTest {

    @Test fun `og tags win in either attribute order and entities are decoded`() {
        val html = """
            <html><head><title>Fallback &amp; ignored</title>
            <META content="Tom &amp; Jerry" property="og:title">
            <meta property='og:site_name' content='Example'/>
            <meta name="description" content="plain description">
            <meta property="og:description" content="A &quot;quoted&quot; blurb">
            </head><body></body></html>
        """.trimIndent()
        val preview = LinkPreview.parse(html)
        assertEquals("Tom & Jerry", preview.title)
        assertEquals("Example", preview.siteName)
        assertEquals("A \"quoted\" blurb", preview.description)
    }

    @Test fun `title tag and meta description fill in when og tags are missing`() {
        val preview = LinkPreview.parse("<title>\n  Just a page \n</title><meta name=\"description\" content=\"d\">")
        assertEquals("Just a page", preview.title)
        assertEquals("d", preview.description)
        assertNull(preview.siteName)
    }

    @Test fun `no tags at all is empty`() {
        assertTrue(LinkPreview.parse("<html><body>hello</body></html>").isEmpty)
    }
}

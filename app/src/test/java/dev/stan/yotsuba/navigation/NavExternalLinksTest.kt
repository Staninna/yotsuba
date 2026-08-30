package dev.stan.yotsuba.navigation

import android.content.Intent
import dev.stan.yotsuba.core.util.Urls.InternalLink
import dev.stan.yotsuba.core.widget.WidgetDeepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavExternalLinksTest {
    @Test
    fun `view data resolves a thread link with post anchor`() {
        assertEquals(
            InternalLink.Thread("g", 123L, 456L),
            ExternalLinks.fromViewData("https://boards.4chan.org/g/thread/123#p456"),
        )
    }

    @Test
    fun `view data resolves a catalog link`() {
        assertEquals(InternalLink.Catalog("a"), ExternalLinks.fromViewData("https://boards.4chan.org/a/catalog"))
    }

    @Test
    fun `shared text picks the first 4chan url and ignores the rest`() {
        val text = "look at this https://example.com/x and https://boards.4channel.org/v/thread/77/title, wild."
        assertEquals(InternalLink.Thread("v", 77L), ExternalLinks.fromSharedText(text))
    }

    @Test
    fun `text without a 4chan url yields nothing`() {
        assertNull(ExternalLinks.fromSharedText("just words https://example.com"))
        assertNull(ExternalLinks.fromSharedText(null))
        assertNull(ExternalLinks.fromViewData(null))
    }

    @Test
    fun `widget extras resolve a thread link without an action`() {
        val intent = Intent()
            .putExtra(WidgetDeepLink.EXTRA_BOARD, "g")
            .putExtra(WidgetDeepLink.EXTRA_THREAD_NO, 42L)
        assertEquals(InternalLink.Thread("g", 42L), ExternalLinks.fromIntent(intent))
    }

    @Test
    fun `plain launch and bad widget extras yield nothing`() {
        assertNull(ExternalLinks.fromIntent(null))
        assertNull(ExternalLinks.fromIntent(Intent()))
        assertNull(ExternalLinks.fromIntent(Intent().putExtra(WidgetDeepLink.EXTRA_BOARD, "g")))
    }

    @Test
    fun `view intent resolves its data`() {
        val intent = Intent(Intent.ACTION_VIEW).setData(android.net.Uri.parse("https://boards.4chan.org/a/catalog"))
        assertEquals(InternalLink.Catalog("a"), ExternalLinks.fromIntent(intent))
    }
}
